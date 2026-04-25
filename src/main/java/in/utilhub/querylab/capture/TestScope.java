package in.utilhub.querylab.capture;

/**
 * The currently-executing test, attached to the executing thread so the Hibernate
 * StatementInspector can credit emissions to it.
 *
 * Note: a single ThreadLocal is fine because Surefire's default execution model is
 * sequential per JVM. Parallel-test users get one scope per worker thread anyway.
 */
public final class TestScope {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TestScope() {}

    public static void start(String testName) {
        CURRENT.set(testName);
    }

    public static void end() {
        CURRENT.remove();
    }

    /** null when we are not currently inside a test (e.g. during context startup). */
    public static String current() {
        return CURRENT.get();
    }
}
