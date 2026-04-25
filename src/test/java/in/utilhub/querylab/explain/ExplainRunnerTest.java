package in.utilhub.querylab.explain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Placeholder substitution is the easy thing to get wrong silently — these locks the surface area.
 * Spring Data + Hibernate emit at least four placeholder dialects, and Postgres' :: cast operator
 * collides with named-param syntax.
 */
class ExplainRunnerTest {

    @Test
    void singleQuestionMark() {
        assertEquals("SELECT * FROM t WHERE id = NULL",
            ExplainRunner.inlineNullsForPlaceholders("SELECT * FROM t WHERE id = ?"));
    }

    @Test
    void positionalQuestionMarks() {
        assertEquals("SELECT * FROM t WHERE a = NULL AND b = NULL",
            ExplainRunner.inlineNullsForPlaceholders("SELECT * FROM t WHERE a = ?1 AND b = ?2"));
    }

    @Test
    void positionalDoubleDigits() {
        assertEquals("WHERE x = NULL",
            ExplainRunner.inlineNullsForPlaceholders("WHERE x = ?42"));
    }

    @Test
    void namedParameters() {
        assertEquals("SELECT * FROM t WHERE name = NULL",
            ExplainRunner.inlineNullsForPlaceholders("SELECT * FROM t WHERE name = :customerName"));
    }

    @Test
    void postgresCastOperatorIsPreserved() {
        // The :: in '1::int' must NOT be replaced — only :name placeholders are.
        assertEquals("SELECT 1::int FROM t WHERE name = NULL",
            ExplainRunner.inlineNullsForPlaceholders("SELECT 1::int FROM t WHERE name = :n"));
    }

    @Test
    void questionMarksInsideStringLiteralsArePreserved() {
        assertEquals("SELECT 'how?' FROM t WHERE x = NULL",
            ExplainRunner.inlineNullsForPlaceholders("SELECT 'how?' FROM t WHERE x = ?"));
    }

    @Test
    void colonsInsideQuotedIdentifiersArePreserved() {
        assertEquals("SELECT \":weird\" FROM t WHERE x = NULL",
            ExplainRunner.inlineNullsForPlaceholders("SELECT \":weird\" FROM t WHERE x = ?"));
    }

    @Test
    void mixOfStylesInOneStatement() {
        assertEquals(
            "SELECT * FROM o WHERE a = NULL AND b = NULL AND c = NULL AND d = NULL",
            ExplainRunner.inlineNullsForPlaceholders(
                "SELECT * FROM o WHERE a = ? AND b = ?1 AND c = :id AND d = ?7"
            )
        );
    }
}
