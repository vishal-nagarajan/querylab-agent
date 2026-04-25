package in.utilhub.querylab.explain;

import in.utilhub.querylab.model.Flag;

/**
 * Builds a flag from a single EXPLAIN result. v0.2 has one shape:
 *   "the plan does a full table read" → severity BAD.
 *
 * If the SQL had a Filter / WHERE → message says "lost_index" (an index would help).
 * If the SQL had no Filter           → message says "full_scan" (probably intentional but worth noting).
 */
public final class LostIndexRule {

    public Flag flagFor(ExtractedQuery q, PlanAnalyzer.PlanFacts facts, String fingerprintHash) {
        if (!facts.hasSeqScan) return null;
        boolean lostIndex = facts.hasFilter;
        String ruleId = lostIndex ? "lost_index" : "full_scan";
        String message = lostIndex
            ? "EXPLAIN shows " + facts.worstAccessPath + " with a filter — an index on the filtered column(s) would replace the full read."
            : "EXPLAIN shows " + facts.worstAccessPath + " with no filter — confirm this is intentional (e.g. a deliberate full export).";
        String suggested = lostIndex
            ? "Add an index covering the WHERE column(s); re-run EXPLAIN to confirm the plan switches to Index Scan."
            : "If this query should be selective, add a WHERE clause and supporting index. If it's intentionally a full read, mark with @QuerylabIgnore(reason=\"...\").";
        return new Flag(ruleId, Flag.Severity.BAD, fingerprintHash, q.site(), message, suggested);
    }
}
