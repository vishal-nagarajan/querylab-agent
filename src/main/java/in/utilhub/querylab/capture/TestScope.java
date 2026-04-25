package in.utilhub.querylab.capture;

/**
 * The currently-executing test, attached to the executing thread so the Hibernate
 * StatementInspector can credit emissions to it.
 *
 * Note: a single ThreadLocal is fine because Surefire's default execution model is
 * sequential per JVM. Parallel-test users get one scope per worker thread anyway.
 *
 * The scope also carries a flag to indicate the test (or its declaring class) was annotated
 * with {@code @QuerylabIgnore} — emissions during such tests are dropped on the floor.
 */
public final class TestScope {

    static final class Frame {
        final String testName;
        final boolean ignored;
        Frame(String testName, boolean ignored) {
            this.testName = testName;
            this.ignored = ignored;
        }
    }

    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private TestScope() {}

    public static void start(String testName) {
        CURRENT.set(new Frame(testName, false));
    }

    public static void start(String testName, boolean ignored) {
        CURRENT.set(new Frame(testName, ignored));
    }

    public static void end() {
        CURRENT.remove();
    }

    /** null when we are not currently inside a test (e.g. during context startup). */
    public static String current() {
        Frame f = CURRENT.get();
        return f == null ? null : f.testName;
    }

    /** True when the active test (or its class) was annotated with {@code @QuerylabIgnore}. */
    public static boolean isCurrentIgnored() {
        Frame f = CURRENT.get();
        return f != null && f.ignored;
    }
}
