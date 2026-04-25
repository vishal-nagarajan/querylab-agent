package in.utilhub.querylab.explain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Placeholder resolution for {@code spring.datasource.url=${DB_URL:jdbc:h2:mem:test}} — make sure
 * we handle both env-var presence and the {@code :default} fallback.
 */
class AppConfigReaderTest {

    @Test
    void plainStringPassesThrough() {
        assertEquals("jdbc:h2:mem:test", AppConfigReader.resolve("jdbc:h2:mem:test"));
    }

    @Test
    void resolvesSystemPropertyOverEnv() {
        System.setProperty("QUERYLAB_TEST_VAR", "from-prop");
        try {
            assertEquals("from-prop-tail", AppConfigReader.resolve("${QUERYLAB_TEST_VAR}-tail"));
        } finally {
            System.clearProperty("QUERYLAB_TEST_VAR");
        }
    }

    @Test
    void usesDefaultWhenMissing() {
        assertEquals("fallback", AppConfigReader.resolve("${NEVER_SET_QUERYLAB_VAR:fallback}"));
    }

    @Test
    void emptyWhenNoDefaultAndMissing() {
        assertEquals("", AppConfigReader.resolve("${NEVER_SET_QUERYLAB_VAR}"));
    }

    @Test
    void multiplePlaceholders() {
        System.setProperty("QUERYLAB_HOST", "localhost");
        System.setProperty("QUERYLAB_PORT", "5432");
        try {
            assertEquals(
                "jdbc:postgresql://localhost:5432/db",
                AppConfigReader.resolve("jdbc:postgresql://${QUERYLAB_HOST}:${QUERYLAB_PORT}/db")
            );
        } finally {
            System.clearProperty("QUERYLAB_HOST");
            System.clearProperty("QUERYLAB_PORT");
        }
    }
}
