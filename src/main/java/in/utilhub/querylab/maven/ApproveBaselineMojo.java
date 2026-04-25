package in.utilhub.querylab.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Maven goal: {@code mvn querylab:approve-baseline}.
 * <p>
 * Promotes the current {@code target/queryreport/run.json} to {@code .querylab/baseline.json}
 * (committed alongside source). Subsequent runs diff against this baseline and only surface
 * net-new findings.
 * <p>
 * The intended workflow:
 *   1. Run scan / explain / runtime to produce a fresh report.
 *   2. Review findings in the HTML report; fix what's wrong.
 *   3. Run {@code mvn querylab:approve-baseline} to record the current state as approved.
 *   4. Commit {@code .querylab/baseline.json} on the branch.
 * Future PRs are then measured against this snapshot — only added findings are flagged.
 */
@Mojo(name = "approve-baseline", threadSafe = true)
public class ApproveBaselineMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/queryreport/run.json", readonly = true)
    private File currentRunFile;

    @Parameter(defaultValue = "${project.basedir}/.querylab/baseline.json", readonly = true)
    private File baselineFile;

    @Override
    public void execute() throws MojoExecutionException {
        Path src = currentRunFile.toPath();
        Path dst = baselineFile.toPath();
        if (!Files.isRegularFile(src)) {
            throw new MojoExecutionException("nothing to promote: " + src + " does not exist. "
                + "Run mvn querylab:scan / explain / test first.");
        }
        try {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            getLog().info("querylab: baseline approved -> " + dst);
            getLog().info("querylab: commit this file. Future PRs will diff against it; only new findings will be flagged.");
        } catch (IOException e) {
            throw new MojoExecutionException("failed to write baseline: " + e.getMessage(), e);
        }
    }
}
