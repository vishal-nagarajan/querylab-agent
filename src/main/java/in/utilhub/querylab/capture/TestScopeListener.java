package in.utilhub.querylab.capture;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * JUnit Platform launcher listener — one per JVM. Discovered via
 * META-INF/services/org.junit.platform.launcher.TestExecutionListener.
 */
public class TestScopeListener implements TestExecutionListener {

    @Override
    public void executionStarted(TestIdentifier id) {
        if (!id.isTest()) return;
        TestScope.start(testNameOf(id));
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (!id.isTest()) return;
        TestScope.end();
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            // For the runtime listener we can't see the Maven `${project.basedir}`, so the baseline
            // file is resolved relative to the working directory: <cwd>/.querylab/baseline.json.
            java.nio.file.Path baseline = java.nio.file.Paths.get(
                System.getProperty("user.dir"), ".querylab", "baseline.json");
            QueryLab.RunResult result = QueryLab.global().buildReport();
            QueryLab.writeReport(result.report(), QueryLab.defaultOutputDir(),
                java.nio.file.Files.isRegularFile(baseline) ? baseline : null);
        } catch (Exception e) {
            // Don't fail the user's build if we can't write our report.
            System.err.println("[querylab] failed to write report: " + e.getMessage());
        }
    }

    private static String testNameOf(TestIdentifier id) {
        return id.getSource()
            .filter(s -> s instanceof MethodSource)
            .map(s -> {
                MethodSource ms = (MethodSource) s;
                return ms.getClassName() + "#" + ms.getMethodName();
            })
            .orElse(id.getDisplayName());
    }
}
