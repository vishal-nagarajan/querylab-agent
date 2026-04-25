package in.utilhub.querylab.capture;

import in.utilhub.querylab.annotations.QuerylabExpect;
import in.utilhub.querylab.annotations.QuerylabIgnore;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.lang.reflect.Method;

/**
 * JUnit Platform launcher listener — one per JVM. Discovered via
 * META-INF/services/org.junit.platform.launcher.TestExecutionListener.
 *
 * On test start we resolve {@code @QuerylabIgnore} (class- or method-level) and
 * {@code @QuerylabExpect} (method-level, repeatable) so the runtime can suppress emissions
 * and flags for explicitly-approved cases.
 */
public class TestScopeListener implements TestExecutionListener {

    @Override
    public void executionStarted(TestIdentifier id) {
        if (!id.isTest()) return;
        String testName = testNameOf(id);
        Method method = resolveMethod(id);
        boolean ignored = isIgnored(method);
        TestScope.start(testName, ignored);
        if (!ignored && method != null) {
            registerExpectations(testName, method);
        }
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

    /** Returns the JUnit-resolved Method behind a TestIdentifier, or null if not resolvable. */
    private static Method resolveMethod(TestIdentifier id) {
        return id.getSource()
            .filter(s -> s instanceof MethodSource)
            .map(s -> (MethodSource) s)
            .map(ms -> {
                try {
                    Class<?> cls = Class.forName(ms.getClassName());
                    return ms.getJavaMethod();
                } catch (ClassNotFoundException e) {
                    return null;
                } catch (RuntimeException e) {
                    return null;
                }
            })
            .orElse(null);
    }

    /** True if either the method or its declaring class carries {@code @QuerylabIgnore}. */
    private static boolean isIgnored(Method method) {
        if (method == null) return false;
        if (method.isAnnotationPresent(QuerylabIgnore.class)) return true;
        return method.getDeclaringClass().isAnnotationPresent(QuerylabIgnore.class);
    }

    private static void registerExpectations(String testName, Method method) {
        QuerylabExpect single = method.getAnnotation(QuerylabExpect.class);
        QuerylabExpect.List multi = method.getAnnotation(QuerylabExpect.List.class);
        if (single != null) {
            QueryLab.global().recordExpectation(testName, single.rule(), single.fingerprintLike(), single.emissions());
        }
        if (multi != null) {
            for (QuerylabExpect e : multi.value()) {
                QueryLab.global().recordExpectation(testName, e.rule(), e.fingerprintLike(), e.emissions());
            }
        }
    }
}
