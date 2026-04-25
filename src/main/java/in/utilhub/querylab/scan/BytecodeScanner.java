package in.utilhub.querylab.scan;

import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;
import in.utilhub.querylab.model.RunReport;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Static-analysis entry point. Walks compiled classes in two passes:
 *   1. Catalog pass — find all entities (with their lazy fields) and all repositories.
 *   2. Detection pass — walk every method in scope and emit Flags for matched patterns.
 */
public final class BytecodeScanner {

    private final Scope scope;
    private final Consumer<String> debug;

    public BytecodeScanner(Scope scope, Consumer<String> debug) {
        this.scope = scope;
        this.debug = debug;
    }

    public RunReport scan(Path classesRoot) throws IOException {
        EntityCatalog entities = new EntityCatalog();
        RepositoryCatalog repos = new RepositoryCatalog();

        // First pass: read ALL classes (even out of scope) into catalogs so the detection pass
        // can resolve relationships across packages.
        forEachClass(classesRoot, cn -> {
            entities.ingest(cn);
            repos.ingest(cn);
        });
        debug.accept("scan: catalog has " + entities.size() + " entities, " + repos.size() + " repos");

        // Second pass: detect patterns, but only on classes the user wants analysed.
        PatternMatcher matcher = new PatternMatcher(entities, repos, debug);
        forEachClass(classesRoot, cn -> {
            if (!scope.accepts(cn.name)) return;
            matcher.analyze(cn);
        });

        return buildReport(matcher.findings());
    }

    private void forEachClass(Path classesRoot, Consumer<ClassNode> sink) throws IOException {
        if (!Files.isDirectory(classesRoot)) return;
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            walk.filter(p -> p.toString().endsWith(".class") && Files.isRegularFile(p))
                .forEach(p -> {
                    try (InputStream in = Files.newInputStream(p)) {
                        ClassNode cn = new ClassNode();
                        new ClassReader(in).accept(cn, ClassReader.SKIP_FRAMES);
                        sink.accept(cn);
                    } catch (IOException e) {
                        debug.accept("could not read " + p + ": " + e.getMessage());
                    }
                });
        }
    }

    private RunReport buildReport(List<PatternMatcher.Finding> findings) {
        // Collapse findings with the same synthetic SQL into fingerprints.
        Map<String, int[]> stats = new HashMap<>(); // hash → [emissionTotal, distinctSites]
        Map<String, String> sqlByHash = new HashMap<>();
        Map<String, Map<String, Integer>> bySite = new HashMap<>();
        List<Flag> flags = new ArrayList<>();

        for (PatternMatcher.Finding f : findings) {
            String sql = f.predictedSql;
            String hash = sha256(sql);
            if (scope.isFingerprintIgnored(hash)) continue;

            sqlByHash.putIfAbsent(hash, sql);
            int[] s = stats.computeIfAbsent(hash, k -> new int[2]);
            s[0] += 1;       // each finding represents one predicted emission site
            s[1] += 1;

            bySite.computeIfAbsent(f.site, k -> new HashMap<>())
                  .merge(hash, 1, Integer::sum);

            flags.add(new Flag(
                f.ruleId,
                Flag.Severity.WARN,
                hash,
                f.site,
                f.message,
                f.suggestedFix
            ));
        }

        List<Fingerprint> fingerprints = new ArrayList<>();
        for (Map.Entry<String, int[]> e : stats.entrySet()) {
            int[] s = e.getValue();
            fingerprints.add(new Fingerprint(e.getKey(), sqlByHash.get(e.getKey()), s[0], s[1]));
        }
        fingerprints.sort((a, b) -> Integer.compare(b.totalEmissions(), a.totalEmissions()));

        return new RunReport(
            Instant.now(),
            findings.size(),
            fingerprints.size(),
            bySite.size(),
            fingerprints,
            bySite,
            flags
        );
    }

    private static String sha256(String s) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
