package in.utilhub.querylab.maven;

import in.utilhub.querylab.capture.QueryLab;
import in.utilhub.querylab.explain.AppConfigReader;
import in.utilhub.querylab.explain.DataSourceConfig;
import in.utilhub.querylab.explain.ExplainRunner;
import in.utilhub.querylab.explain.ExtractedQuery;
import in.utilhub.querylab.explain.LostIndexRule;
import in.utilhub.querylab.explain.PlanAnalyzer;
import in.utilhub.querylab.explain.QueryExtractor;
import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;
import in.utilhub.querylab.model.RunReport;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maven goal: {@code mvn querylab:explain}.
 * <p>
 * Statically extracts native {@code @Query} annotations from compiled repositories, connects
 * to the user's dev DB, runs EXPLAIN, and emits BAD-severity flags for queries whose plan is
 * a full table read (lost-index / full-scan). No queries execute — EXPLAIN only.
 */
@Mojo(name = "explain", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ExplainMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File classesDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/main/resources", readonly = true)
    private File resourcesDirectory;

    @Parameter(defaultValue = "${project.build.directory}/queryreport", readonly = true)
    private File outputDirectory;

    /** Override the JDBC URL. If absent, we read application.yml. */
    @Parameter(property = "querylab.explain.url")
    private String jdbcUrl;

    @Parameter(property = "querylab.explain.user")
    private String jdbcUser;

    @Parameter(property = "querylab.explain.password")
    private String jdbcPassword;

    /** Active Spring profile to resolve {@code application-{profile}.yml}. */
    @Parameter(property = "spring.profiles.active", defaultValue = "")
    private String springProfile;

    @Parameter(property = "querylab.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("querylab:explain skipped (-Dquerylab.skip=true)");
            return;
        }
        if (!classesDirectory.isDirectory()) {
            getLog().info("querylab:explain: no compiled classes — nothing to do");
            return;
        }

        DataSourceConfig cfg = resolveConfig();
        if (cfg == null) {
            getLog().warn("querylab:explain: no datasource configured. Set -Dquerylab.explain.url=... or define spring.datasource in application.yml. Skipping.");
            return;
        }
        getLog().info("querylab:explain using " + cfg.url() + " (" + cfg.dialect() + ", from " + cfg.source() + ")");

        try {
            QueryExtractor extractor = new QueryExtractor(getLog()::debug);
            List<ExtractedQuery> all = extractor.extract(classesDirectory.toPath());
            List<ExtractedQuery> nat = new ArrayList<>();
            for (ExtractedQuery q : all) if (q.nativeQuery()) nat.add(q);
            getLog().info("querylab:explain found " + all.size() + " @Query methods (" + nat.size() + " native, eligible for v0.2 EXPLAIN)");

            if (nat.isEmpty()) {
                writeEmptyReport();
                return;
            }

            ExplainRunner runner = new ExplainRunner(getLog()::debug);
            Map<ExtractedQuery, String> plans = runner.explainAll(cfg, nat);

            PlanAnalyzer analyzer = new PlanAnalyzer();
            LostIndexRule rule = new LostIndexRule();

            List<Fingerprint> fingerprints = new ArrayList<>();
            Map<String, Map<String, Integer>> bySite = new HashMap<>();
            List<Flag> flags = new ArrayList<>();
            int explained = 0, flagged = 0;

            for (Map.Entry<ExtractedQuery, String> e : plans.entrySet()) {
                explained++;
                ExtractedQuery q = e.getKey();
                PlanAnalyzer.PlanFacts facts = analyzer.analyze(cfg.dialect(), e.getValue());
                String hash = sha256(q.sql());
                fingerprints.add(new Fingerprint(hash, q.sql(), 1, 1));
                bySite.computeIfAbsent(q.site(), k -> new HashMap<>()).put(hash, 1);
                Flag f = rule.flagFor(q, facts, hash);
                if (f != null) { flags.add(f); flagged++; }
            }

            RunReport report = new RunReport(
                Instant.now(),
                explained,
                fingerprints.size(),
                bySite.size(),
                fingerprints,
                bySite,
                flags
            );
            Path out = outputDirectory.toPath();
            QueryLab.writeReport(report, out);
            getLog().info("querylab:explain complete: " + explained + " queries planned, " + flagged + " flagged. report → " + out);

        } catch (Exception e) {
            throw new MojoExecutionException("querylab:explain failed: " + e.getMessage(), e);
        }
    }

    private DataSourceConfig resolveConfig() {
        if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
            return new DataSourceConfig(jdbcUrl, jdbcUser, jdbcPassword, "param");
        }
        if (resourcesDirectory.isDirectory()) {
            return new AppConfigReader().read(resourcesDirectory.toPath(), springProfile);
        }
        return null;
    }

    private void writeEmptyReport() throws Exception {
        RunReport empty = new RunReport(
            Instant.now(),
            0, 0, 0,
            new ArrayList<>(),
            new HashMap<>(),
            new ArrayList<>()
        );
        QueryLab.writeReport(empty, outputDirectory.toPath());
    }

    private static String sha256(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
