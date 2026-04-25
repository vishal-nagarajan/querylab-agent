package in.utilhub.querylab.maven;

import in.utilhub.querylab.capture.QueryLab;
import in.utilhub.querylab.model.RunReport;
import in.utilhub.querylab.scan.BytecodeScanner;
import in.utilhub.querylab.scan.Scope;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;

/**
 * Maven goal: {@code mvn querylab:scan}.
 * <p>
 * Statically walks the project's compiled classes (target/classes) using ASM, builds an entity +
 * repository catalog, and detects N+1 candidates from bytecode patterns. Writes the same
 * {@link RunReport} shape as the runtime listener, but with severity=WARN and mode=STATIC.
 */
@Mojo(name = "scan", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true, requiresDependencyResolution = org.apache.maven.plugins.annotations.ResolutionScope.COMPILE)
public class ScanMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File classesDirectory;

    @Parameter(defaultValue = "${project.build.directory}/queryreport", readonly = true)
    private File outputDirectory;

    @Parameter(property = "querylab.scope", defaultValue = "${project.basedir}/.querylab/scope.yml")
    private File scopeFile;

    @Parameter(property = "querylab.baseline", defaultValue = "${project.basedir}/.querylab/baseline.json")
    private File baselineFile;

    @Parameter(property = "querylab.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("querylab:scan skipped (-Dquerylab.skip=true)");
            return;
        }
        if (!classesDirectory.isDirectory()) {
            getLog().info("querylab:scan: no compiled classes at " + classesDirectory + " — nothing to scan");
            return;
        }

        Scope scope = Scope.load(scopeFile.toPath(), getLog()::warn);
        getLog().info("querylab:scan scanning " + classesDirectory + (scope.isDefault() ? "" : " (scoped)"));

        try {
            BytecodeScanner scanner = new BytecodeScanner(scope, getLog()::debug);
            RunReport report = scanner.scan(classesDirectory.toPath());

            Path out = outputDirectory.toPath();
            QueryLab.writeReport(report, out, baselineFile.toPath());

            getLog().info("querylab:scan complete: "
                + report.distinctFingerprints() + " fingerprints, "
                + report.flags().size() + " flags raised. report → " + out);
        } catch (Exception e) {
            throw new MojoExecutionException("querylab:scan failed: " + e.getMessage(), e);
        }
    }
}
