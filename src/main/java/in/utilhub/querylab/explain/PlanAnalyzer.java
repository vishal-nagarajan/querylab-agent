package in.utilhub.querylab.explain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the structural facts we care about out of an EXPLAIN plan text. Dialect-specific patterns,
 * dialect-agnostic output.
 */
public final class PlanAnalyzer {

    public static final class PlanFacts {
        public final boolean hasSeqScan;
        public final boolean hasFilter;       // suggests an index would help
        public final String  worstAccessPath; // e.g. "Seq Scan on shipments"
        public final String  rawPlan;

        PlanFacts(boolean hasSeqScan, boolean hasFilter, String worstAccessPath, String rawPlan) {
            this.hasSeqScan = hasSeqScan;
            this.hasFilter = hasFilter;
            this.worstAccessPath = worstAccessPath;
            this.rawPlan = rawPlan;
        }
    }

    // Postgres: "Seq Scan on customers  (cost=0.00..23.50 rows=1350 width=...)"
    private static final Pattern PG_SEQ_SCAN = Pattern.compile("Seq Scan on (\\S+)", Pattern.CASE_INSENSITIVE);
    // H2: "tableScan (...)" or "/* PUBLIC.SHIPMENTS.tableScan */"
    private static final Pattern H2_TABLE_SCAN = Pattern.compile("(?i)tableScan");
    // MySQL: "type: ALL"  in the EXPLAIN output (suggests no index)
    private static final Pattern MYSQL_TYPE_ALL = Pattern.compile("type:\\s*ALL", Pattern.CASE_INSENSITIVE);

    private static final Pattern FILTER_HINT = Pattern.compile("(?i)\\b(Filter|filter:|WHERE)\\b");

    public PlanFacts analyze(DataSourceConfig.Dialect dialect, String rawPlan) {
        if (rawPlan == null) return new PlanFacts(false, false, null, null);

        boolean seq = false;
        String access = null;
        switch (dialect) {
            case POSTGRES: {
                Matcher m = PG_SEQ_SCAN.matcher(rawPlan);
                if (m.find()) { seq = true; access = "Seq Scan on " + m.group(1); }
                break;
            }
            case H2: {
                if (H2_TABLE_SCAN.matcher(rawPlan).find()) {
                    seq = true; access = "tableScan";
                }
                break;
            }
            case MYSQL: {
                if (MYSQL_TYPE_ALL.matcher(rawPlan).find()) {
                    seq = true; access = "type: ALL";
                }
                break;
            }
            default: break;
        }

        boolean hasFilter = FILTER_HINT.matcher(rawPlan).find();
        return new PlanFacts(seq, hasFilter, access, rawPlan);
    }
}
