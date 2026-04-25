package in.utilhub.querylab.capture;

import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;
import in.utilhub.querylab.model.RunReport;
import in.utilhub.querylab.report.HtmlReportWriter;
import in.utilhub.querylab.report.JsonReportWriter;
import in.utilhub.querylab.rules.NPlusOneRule;
import in.utilhub.querylab.rules.RuleEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-level singleton holding the run state. Captures emissions, builds the report at end-of-plan.
 *
 * Thread-safe: capture runs on test threads, report build runs single-threaded after plan completes.
 */
public final class QueryLab {

    private static final QueryLab INSTANCE = new QueryLab();

    /** Test method name → fingerprint hash → count of emissions inside that test. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> byTest = new ConcurrentHashMap<>();
    /** Fingerprint hash → its canonical SQL template. First-write-wins. */
    private final ConcurrentHashMap<String, String> sqlByHash = new ConcurrentHashMap<>();

    private final AtomicLong totalEmissions = new AtomicLong();

    private QueryLab() {}

    public static QueryLab global() {
        return INSTANCE;
    }

    /** Called by QueryCaptureInspector for every SQL Hibernate emits. */
    public void recordEmission(String testName, String sql) {
        if (testName == null) {
            // Outside a test (context startup, schema bootstrap) — ignore in v0.
            return;
        }
        String normalized = normalize(sql);
        String hash = sha256(normalized);
        sqlByHash.putIfAbsent(hash, normalized);

        byTest
            .computeIfAbsent(testName, k -> new ConcurrentHashMap<>())
            .merge(hash, 1, Integer::sum);

        totalEmissions.incrementAndGet();
    }

    /** Build the report, run rules, and write the artifacts. */
    public RunReport buildAndWriteReport(Path outputDir) throws IOException {
        RunReport report = build();
        Files.createDirectories(outputDir);
        new JsonReportWriter().write(report, outputDir.resolve("run.json"));
        new HtmlReportWriter().write(report, outputDir.resolve("index.html"));
        return report;
    }

    private RunReport build() {
        // Aggregate fingerprint stats
        Map<String, int[]> stats = new HashMap<>();   // hash → [totalEmissions, distinctTests]
        for (Map.Entry<String, ConcurrentHashMap<String, Integer>> testEntry : byTest.entrySet()) {
            for (Map.Entry<String, Integer> e : testEntry.getValue().entrySet()) {
                int[] s = stats.computeIfAbsent(e.getKey(), k -> new int[2]);
                s[0] += e.getValue();
                s[1] += 1;
            }
        }
        List<Fingerprint> fingerprints = new ArrayList<>();
        for (Map.Entry<String, int[]> e : stats.entrySet()) {
            String hash = e.getKey();
            int[] s = e.getValue();
            fingerprints.add(new Fingerprint(hash, sqlByHash.getOrDefault(hash, ""), s[0], s[1]));
        }
        // Sort: most-emitting first
        fingerprints.sort((a, b) -> Integer.compare(b.totalEmissions(), a.totalEmissions()));

        // Snapshot emissionsByTest as an unmodifiable nested map
        Map<String, Map<String, Integer>> snapshot = new HashMap<>();
        for (Map.Entry<String, ConcurrentHashMap<String, Integer>> e : byTest.entrySet()) {
            snapshot.put(e.getKey(), new HashMap<>(e.getValue()));
        }

        // Run rules
        RuleEngine engine = new RuleEngine();
        engine.register(new NPlusOneRule());
        List<Flag> flags = engine.runOver(fingerprints, snapshot);

        return new RunReport(
            Instant.now(),
            totalEmissions.get(),
            fingerprints.size(),
            byTest.size(),
            fingerprints,
            snapshot,
            flags
        );
    }

    /** Resolve where target/queryreport/ lives based on the working directory. */
    public static Path defaultOutputDir() {
        return Paths.get(System.getProperty("user.dir")).resolve("target").resolve("queryreport");
    }

    /** Reset state (used by tests). */
    public void resetForTesting() {
        byTest.clear();
        sqlByHash.clear();
        totalEmissions.set(0);
    }

    // -------- helpers --------

    /** Strip whitespace runs and lowercase keywords for stable hashing. */
    static String normalize(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
