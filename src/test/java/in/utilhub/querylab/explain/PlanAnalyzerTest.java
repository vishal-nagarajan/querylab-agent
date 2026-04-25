package in.utilhub.querylab.explain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-dialect detection of the "this query does a full read" pattern. Output strings are
 * dialect-stable across releases for the patterns we look at.
 */
class PlanAnalyzerTest {

    @Test
    void postgresSeqScan() {
        String plan = "Seq Scan on shipments  (cost=0.00..184321.00 rows=10200000 width=120)\n"
                    + "  Filter: ((tracking)::text = 'abc'::text)";
        PlanAnalyzer.PlanFacts f = new PlanAnalyzer().analyze(DataSourceConfig.Dialect.POSTGRES, plan);
        assertTrue(f.hasSeqScan);
        assertTrue(f.hasFilter);
        assertEquals("Seq Scan on shipments", f.worstAccessPath);
    }

    @Test
    void postgresIndexScanIsClean() {
        String plan = "Index Scan using shipments_lookup_idx on shipments  (cost=0.42..8.45 rows=1)";
        PlanAnalyzer.PlanFacts f = new PlanAnalyzer().analyze(DataSourceConfig.Dialect.POSTGRES, plan);
        assertFalse(f.hasSeqScan);
    }

    @Test
    void h2TableScan() {
        String plan = "SELECT LINE_ITEM.ID, LINE_ITEM.DESCRIPTION FROM PUBLIC.LINE_ITEM /* PUBLIC.LINE_ITEM.tableScan */ WHERE LINE_ITEM.DESCRIPTION = 'a'";
        PlanAnalyzer.PlanFacts f = new PlanAnalyzer().analyze(DataSourceConfig.Dialect.H2, plan);
        assertTrue(f.hasSeqScan);
        assertEquals("tableScan", f.worstAccessPath);
    }

    @Test
    void mysqlTypeAll() {
        // MySQL's text EXPLAIN includes a row like "1  SIMPLE  shipments   ALL    NULL   ..."
        String plan = "id: 1\nselect_type: SIMPLE\ntable: shipments\ntype: ALL\npossible_keys: NULL";
        PlanAnalyzer.PlanFacts f = new PlanAnalyzer().analyze(DataSourceConfig.Dialect.MYSQL, plan);
        assertTrue(f.hasSeqScan);
    }

    @Test
    void unknownDialectReturnsClean() {
        PlanAnalyzer.PlanFacts f = new PlanAnalyzer().analyze(DataSourceConfig.Dialect.UNKNOWN, "anything");
        assertFalse(f.hasSeqScan);
    }
}
