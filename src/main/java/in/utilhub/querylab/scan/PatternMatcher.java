package in.utilhub.querylab.scan;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * Looks at every method in the project and emits {@link Finding}s when it sees:
 *   - Pattern A: getter call on a LAZY association of an entity, inside a loop body
 *   - Pattern B: invocation of a repository method, inside a loop body
 *
 * Loop detection: any back-edge — a JUMP instruction whose target label appears earlier in the
 * instruction list. Anything between the target label and the jump is "inside a loop." This is
 * intentionally cheap and handles the common cases (foreach, classic for, while, do/while, and
 * stream forEach via lambdas as separate methods).
 */
final class PatternMatcher {

    static final class Finding {
        final String ruleId;
        final String site;          // "ClassName#methodName"
        final String predictedSql;  // synthetic SQL shape
        final String message;
        final String suggestedFix;
        Finding(String ruleId, String site, String predictedSql, String message, String suggestedFix) {
            this.ruleId = ruleId;
            this.site = site;
            this.predictedSql = predictedSql;
            this.message = message;
            this.suggestedFix = suggestedFix;
        }
    }

    private final EntityCatalog entities;
    private final RepositoryCatalog repos;
    private final Consumer<String> debug;
    private final List<Finding> findings = new ArrayList<>();

    PatternMatcher(EntityCatalog entities, RepositoryCatalog repos, Consumer<String> debug) {
        this.entities = entities;
        this.repos = repos;
        this.debug = debug;
    }

    List<Finding> findings() { return findings; }

    void analyze(ClassNode cn) {
        if (cn.methods == null) return;
        for (MethodNode m : cn.methods) {
            analyzeMethod(cn, m);
        }
    }

    private void analyzeMethod(ClassNode cn, MethodNode m) {
        InsnList ins = m.instructions;
        if (ins == null || ins.size() == 0) return;

        // Lambda body convention: javac emits `lambda$<owner>$<n>` synthetic methods. They run
        // once per iteration when passed into Stream.map / forEach / flatMap / Iterable.forEach,
        // so for N+1 detection we treat their whole body as an implicit loop region.
        boolean isLambdaBody = m.name.startsWith("lambda$") && (m.access & org.objectweb.asm.Opcodes.ACC_SYNTHETIC) != 0;

        // Index every label so we can detect back-edges (jump target appears earlier than jump).
        Map<LabelNode, Integer> labelIndex = new HashMap<>();
        AbstractInsnNode cur = ins.getFirst();
        int idx = 0;
        while (cur != null) {
            if (cur instanceof LabelNode) labelIndex.put((LabelNode) cur, idx);
            cur = cur.getNext();
            idx++;
        }

        // Find all back-edge ranges [targetIdx .. jumpIdx]. A position is "in loop" if covered.
        boolean[] inLoop = new boolean[idx];
        if (isLambdaBody) {
            java.util.Arrays.fill(inLoop, true);
        }
        cur = ins.getFirst();
        int i = 0;
        while (cur != null) {
            if (cur instanceof JumpInsnNode) {
                JumpInsnNode jn = (JumpInsnNode) cur;
                Integer t = labelIndex.get(jn.label);
                if (t != null && t < i) {
                    for (int k = t; k <= i; k++) inLoop[k] = true;
                }
            }
            cur = cur.getNext();
            i++;
        }

        // Walk again and flag pattern matches that fall inside a loop.
        cur = ins.getFirst();
        i = 0;
        int lastLine = -1;
        while (cur != null) {
            if (cur instanceof LineNumberNode) lastLine = ((LineNumberNode) cur).line;
            if (inLoop[i]) {
                // Pattern A: GETFIELD or invocation of a getter on a lazy entity field
                if (cur.getOpcode() == GETFIELD) {
                    FieldInsnNode fn = (FieldInsnNode) cur;
                    if (entities.isLazyField(fn.owner, fn.name)) {
                        emitPatternA(cn, m, fn, lastLine);
                    }
                } else if (cur.getOpcode() == INVOKEVIRTUAL || cur.getOpcode() == INVOKEINTERFACE) {
                    MethodInsnNode mi = (MethodInsnNode) cur;
                    // Pattern B: invocation on a repository interface
                    if (cur.getOpcode() == INVOKEINTERFACE && repos.isRepositoryInterface(mi.owner)) {
                        emitPatternB(cn, m, mi, lastLine);
                    } else {
                        // Pattern A flavour: invocation of a getXxx() on a lazy entity field
                        if (mi.name.startsWith("get") && entities.isEntity(mi.owner)) {
                            String guessedField = lowerFirst(mi.name.substring(3));
                            if (entities.isLazyField(mi.owner, guessedField)) {
                                emitPatternA(cn, m, mi, guessedField, lastLine);
                            }
                        }
                    }
                }
            }
            cur = cur.getNext();
            i++;
        }
    }

    private void emitPatternA(ClassNode cn, MethodNode m, FieldInsnNode fn, int line) {
        String entitySimpleName = simpleName(fn.owner);
        String predicted = "select " + entitySimpleName + "_lazy_" + fn.name + " from " + entitySimpleName + " where parent_id = ?";
        String site = humanSite(cn, m, line);
        findings.add(new Finding(
            "n_plus_one_lazy_access",
            site,
            predicted,
            "lazy field " + entitySimpleName + "." + fn.name + " accessed inside a loop — likely N+1",
            "Use a JOIN FETCH, @EntityGraph, or @BatchSize on " + entitySimpleName + "." + fn.name + " to collapse the per-iteration loads."
        ));
    }

    private void emitPatternA(ClassNode cn, MethodNode m, MethodInsnNode mi, String fieldName, int line) {
        String entitySimpleName = simpleName(mi.owner);
        String predicted = "select " + entitySimpleName + "_lazy_" + fieldName + " from " + entitySimpleName + " where parent_id = ?";
        String site = humanSite(cn, m, line);
        findings.add(new Finding(
            "n_plus_one_lazy_access",
            site,
            predicted,
            "getter " + entitySimpleName + "." + mi.name + "() on a lazy field, inside a loop — likely N+1",
            "Use a JOIN FETCH, @EntityGraph, or @BatchSize on " + entitySimpleName + "." + fieldName + " to collapse the per-iteration loads."
        ));
    }

    private void emitPatternB(ClassNode cn, MethodNode m, MethodInsnNode mi, int line) {
        String repoSimpleName = simpleName(mi.owner);
        // Synthetic SQL: we don't know the entity's table, but we can express the shape uniquely.
        String args = parameterShape(mi.desc);
        String predicted = repoSimpleName + "." + mi.name + "(" + args + ") -- per-iteration repository call";
        String site = humanSite(cn, m, line);
        findings.add(new Finding(
            "n_plus_one_repo_call",
            site,
            predicted,
            "repository call " + repoSimpleName + "." + mi.name + "() inside a loop — N+1 by construction",
            "Hoist this call out of the loop or batch the IDs (findAllById, custom @Query with IN-clause, or build a Map outside the loop)."
        ));
    }

    private static String parameterShape(String methodDesc) {
        Type[] args = Type.getArgumentTypes(methodDesc);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(simpleName(args[i].getInternalName() == null ? args[i].getDescriptor() : args[i].getInternalName()));
        }
        return sb.toString();
    }

    private static String humanSite(ClassNode cn, MethodNode m, int line) {
        return cn.name.replace('/', '.') + "#" + m.name + (line > 0 ? ":" + line : "");
    }

    private static String simpleName(String internalOrDescriptor) {
        if (internalOrDescriptor == null) return "?";
        String s = internalOrDescriptor.replace('/', '.');
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private static String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
