package in.utilhub.querylab.scan;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * Looks at every method in the project and emits {@link Finding}s when it sees:
 *   - Pattern A: getter call on a LAZY association of an entity, inside a loop body
 *   - Pattern B: invocation of a repository method, inside a loop body
 *
 * Loop detection: any back-edge — a JUMP instruction whose target label appears earlier in the
 * instruction list. Anything between the target label and the jump is "inside a loop." This is
 * intentionally cheap and handles classic for / while / do-while / for-each.
 *
 * Stream lambdas: Java compiles {@code list.stream().map(repo::findById)} into a synthetic method
 * named {@code lambda$<owner>$<n>}. Since the lambda runs once per stream element, we treat its
 * body as an implicit loop region — but only if we can prove the lambda was actually passed to a
 * stream-context consumer (Stream.map / forEach / filter / Iterable.forEach / Map.forEach / etc.).
 * Lambdas to {@code Optional.map}, {@code CompletableFuture.thenApply}, etc. are NOT treated as
 * loops, removing a meaningful class of false positives.
 *
 * Bean-property recognition: getters are matched against {@code getXxx} / {@code isXxx} /
 * {@code hasXxx} naming conventions.
 */
final class PatternMatcher {

    static final class Finding {
        final String ruleId;
        final String site;
        final String predictedSql;
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

    /** Bytecode owner+name pairs that consume a lambda inside an iteration. */
    private static final Set<String> STREAM_CONSUMERS = new HashSet<>(Arrays.asList(
        "java/util/stream/Stream.forEach",
        "java/util/stream/Stream.forEachOrdered",
        "java/util/stream/Stream.map",
        "java/util/stream/Stream.mapToInt",
        "java/util/stream/Stream.mapToLong",
        "java/util/stream/Stream.mapToDouble",
        "java/util/stream/Stream.mapToObj",
        "java/util/stream/Stream.flatMap",
        "java/util/stream/Stream.flatMapToInt",
        "java/util/stream/Stream.flatMapToLong",
        "java/util/stream/Stream.flatMapToDouble",
        "java/util/stream/Stream.filter",
        "java/util/stream/Stream.peek",
        "java/util/stream/IntStream.forEach",
        "java/util/stream/IntStream.forEachOrdered",
        "java/util/stream/IntStream.map",
        "java/util/stream/IntStream.mapToObj",
        "java/util/stream/IntStream.filter",
        "java/util/stream/LongStream.forEach",
        "java/util/stream/LongStream.map",
        "java/util/stream/LongStream.mapToObj",
        "java/util/stream/LongStream.filter",
        "java/util/stream/DoubleStream.forEach",
        "java/util/stream/DoubleStream.map",
        "java/util/stream/DoubleStream.mapToObj",
        "java/util/stream/DoubleStream.filter",
        "java/lang/Iterable.forEach",
        "java/util/Map.forEach",
        "java/util/Map.replaceAll",
        "java/util/Collection.removeIf"
    ));

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
        // Per-class pre-pass: which `lambda$*` synthetic methods were handed to a stream consumer?
        // Heuristic: if a method body contains both an INVOKEDYNAMIC creating a lambda for THIS
        // class AND a stream-consumer call, mark the lambda as stream-context. Imprecise but
        // catches the common case (chained streams in one method) without false-attributing
        // Optional.map / CompletableFuture lambdas.
        Set<String> streamLambdas = findStreamContextLambdas(cn);
        for (MethodNode m : cn.methods) {
            if (Scope.isMethodIgnored(m)) {
                debug.accept("scope: skipping " + cn.name + "#" + m.name + " (@QuerylabIgnore)");
                continue;
            }
            analyzeMethod(cn, m, streamLambdas);
        }
    }

    private Set<String> findStreamContextLambdas(ClassNode cn) {
        Set<String> ownLambdas = new HashSet<>();
        Set<String> streamLambdas = new HashSet<>();
        for (MethodNode m : cn.methods) {
            boolean methodHasStreamConsumer = false;
            List<String> lambdasCreatedHere = new ArrayList<>();
            AbstractInsnNode cur = m.instructions != null ? m.instructions.getFirst() : null;
            while (cur != null) {
                if (cur instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode idn = (InvokeDynamicInsnNode) cur;
                    if (idn.bsm != null && "java/lang/invoke/LambdaMetafactory".equals(idn.bsm.getOwner())
                        && idn.bsmArgs != null && idn.bsmArgs.length >= 2 && idn.bsmArgs[1] instanceof Handle) {
                        Handle h = (Handle) idn.bsmArgs[1];
                        if (cn.name.equals(h.getOwner()) && h.getName().startsWith("lambda$")) {
                            lambdasCreatedHere.add(h.getName());
                        }
                    }
                } else if ((cur.getOpcode() == INVOKEINTERFACE || cur.getOpcode() == INVOKEVIRTUAL)
                    && cur instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) cur;
                    if (STREAM_CONSUMERS.contains(mi.owner + "." + mi.name)) {
                        methodHasStreamConsumer = true;
                    }
                }
                cur = cur.getNext();
            }
            if (methodHasStreamConsumer) streamLambdas.addAll(lambdasCreatedHere);
            ownLambdas.addAll(lambdasCreatedHere);
        }
        debug.accept("stream-context lambdas in " + cn.name + ": "
            + streamLambdas.size() + "/" + ownLambdas.size());
        return streamLambdas;
    }

    private void analyzeMethod(ClassNode cn, MethodNode m, Set<String> streamLambdas) {
        InsnList ins = m.instructions;
        if (ins == null || ins.size() == 0) return;

        boolean isLambdaBody = m.name.startsWith("lambda$") && (m.access & ACC_SYNTHETIC) != 0;
        boolean isStreamLambda = isLambdaBody && streamLambdas.contains(m.name);

        // Index every label so we can detect back-edges (jump target appears earlier than jump).
        Map<LabelNode, Integer> labelIndex = new HashMap<>();
        AbstractInsnNode cur = ins.getFirst();
        int idx = 0;
        while (cur != null) {
            if (cur instanceof LabelNode) labelIndex.put((LabelNode) cur, idx);
            cur = cur.getNext();
            idx++;
        }

        // Mark in-loop positions. Stream lambdas: the WHOLE body is implicit iteration.
        boolean[] inLoop = new boolean[idx];
        if (isStreamLambda) {
            Arrays.fill(inLoop, true);
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
                if (cur.getOpcode() == GETFIELD) {
                    FieldInsnNode fn = (FieldInsnNode) cur;
                    if (entities.isLazyField(fn.owner, fn.name)) {
                        emitPatternA(cn, m, fn, lastLine);
                    }
                } else if (cur.getOpcode() == INVOKEVIRTUAL || cur.getOpcode() == INVOKEINTERFACE) {
                    MethodInsnNode mi = (MethodInsnNode) cur;
                    if (cur.getOpcode() == INVOKEINTERFACE && repos.isRepositoryInterface(mi.owner)) {
                        emitPatternB(cn, m, mi, lastLine);
                    } else if (entities.isEntity(mi.owner)) {
                        String field = beanPropertyFromGetter(mi.name);
                        if (field != null && entities.isLazyField(mi.owner, field)) {
                            emitPatternA(cn, m, mi, field, lastLine);
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
            "lazy field " + entitySimpleName + "." + fn.name + " accessed inside a loop -- likely N+1",
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
            "getter " + entitySimpleName + "." + mi.name + "() on a lazy field, inside a loop -- likely N+1",
            "Use a JOIN FETCH, @EntityGraph, or @BatchSize on " + entitySimpleName + "." + fieldName + " to collapse the per-iteration loads."
        ));
    }

    private void emitPatternB(ClassNode cn, MethodNode m, MethodInsnNode mi, int line) {
        String repoSimpleName = simpleName(mi.owner);
        String args = parameterShape(mi.desc);
        String predicted = repoSimpleName + "." + mi.name + "(" + args + ") -- per-iteration repository call";
        String site = humanSite(cn, m, line);
        findings.add(new Finding(
            "n_plus_one_repo_call",
            site,
            predicted,
            "repository call " + repoSimpleName + "." + mi.name + "() inside a loop -- N+1 by construction",
            "Hoist this call out of the loop or batch the IDs (findAllById, custom @Query with IN-clause, or build a Map outside the loop)."
        ));
    }

    /** Bean-property name extracted from a getter method name, or null if it doesn't look like one. */
    static String beanPropertyFromGetter(String methodName) {
        if (methodName == null) return null;
        if (methodName.startsWith("get") && methodName.length() > 3
            && Character.isUpperCase(methodName.charAt(3))) {
            return lowerFirst(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2
            && Character.isUpperCase(methodName.charAt(2))) {
            return lowerFirst(methodName.substring(2));
        }
        if (methodName.startsWith("has") && methodName.length() > 3
            && Character.isUpperCase(methodName.charAt(3))) {
            return lowerFirst(methodName.substring(3));
        }
        return null;
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
