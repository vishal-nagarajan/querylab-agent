package in.utilhub.querylab.scan;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads .querylab/scope.yml — defines what packages to analyze, what to skip, severity overrides,
 * and rule suppressions. Absent file → analyze everything.
 *
 * Expected YAML:
 *   analyze:
 *     include:
 *       - "in.utilhub.payments.**"
 *     exclude:
 *       - "**.legacy.**"
 *   ignore_fingerprints:
 *     - "a3f2…"
 */
public final class Scope {

    private final List<String> include;
    private final List<String> exclude;
    private final List<String> ignoreFingerprints;
    private final boolean isDefault;

    private Scope(List<String> include, List<String> exclude, List<String> ignoreFingerprints, boolean isDefault) {
        this.include = include;
        this.exclude = exclude;
        this.ignoreFingerprints = ignoreFingerprints;
        this.isDefault = isDefault;
    }

    public static Scope defaultScope() {
        return new Scope(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            true
        );
    }

    @SuppressWarnings("unchecked")
    public static Scope load(Path scopeFile, Consumer<String> warn) {
        if (!Files.isRegularFile(scopeFile)) {
            return defaultScope();
        }
        try (InputStream in = Files.newInputStream(scopeFile)) {
            Map<String, Object> root = new Yaml().load(in);
            if (root == null) return defaultScope();

            Map<String, Object> analyze = (Map<String, Object>) root.getOrDefault("analyze", Collections.emptyMap());
            List<String> include = strList(analyze.get("include"));
            List<String> exclude = strList(analyze.get("exclude"));
            List<String> ignoreFp = strList(root.get("ignore_fingerprints"));
            return new Scope(include, exclude, ignoreFp, false);
        } catch (IOException | RuntimeException e) {
            warn.accept("could not read " + scopeFile + ": " + e.getMessage() + " — falling back to default scope");
            return defaultScope();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        if (o instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object e : (List<Object>) o) {
                if (e != null) out.add(e.toString());
            }
            return out;
        }
        return Collections.emptyList();
    }

    /** Should we analyze this binary class name (e.g. {@code com/foo/bar/Baz})? */
    public boolean accepts(String internalName) {
        String dotted = internalName.replace('/', '.');
        if (!include.isEmpty()) {
            boolean matched = false;
            for (String pat : include) if (matches(pat, dotted)) { matched = true; break; }
            if (!matched) return false;
        }
        for (String pat : exclude) if (matches(pat, dotted)) return false;
        return true;
    }

    private static final String IGNORE_DESC_JAKARTA = "Lin/utilhub/querylab/annotations/QuerylabIgnore;";

    /** True if this class carries {@code @QuerylabIgnore} at type level. */
    public static boolean isClassIgnored(org.objectweb.asm.tree.ClassNode cn) {
        return hasIgnore(cn.visibleAnnotations) || hasIgnore(cn.invisibleAnnotations);
    }

    /** True if this method carries {@code @QuerylabIgnore} (class-level ignore handled separately). */
    public static boolean isMethodIgnored(org.objectweb.asm.tree.MethodNode mn) {
        return hasIgnore(mn.visibleAnnotations) || hasIgnore(mn.invisibleAnnotations);
    }

    private static boolean hasIgnore(java.util.List<org.objectweb.asm.tree.AnnotationNode> anns) {
        if (anns == null) return false;
        for (org.objectweb.asm.tree.AnnotationNode an : anns) {
            if (IGNORE_DESC_JAKARTA.equals(an.desc)) return true;
        }
        return false;
    }

    public boolean isFingerprintIgnored(String hash) {
        for (String h : ignoreFingerprints) if (hash.startsWith(h)) return true;
        return false;
    }

    public boolean isDefault() {
        return isDefault;
    }

    /** Glob-ish matcher: ** = any number of segments, * = any chars within a segment. */
    static boolean matches(String pattern, String dotted) {
        String regex = "^" + pattern
            .replace(".", "\\.")
            .replace("**", "@@DBL@@")
            .replace("*", "[^.]*")
            .replace("@@DBL@@", ".*") + "$";
        return dotted.matches(regex);
    }
}
