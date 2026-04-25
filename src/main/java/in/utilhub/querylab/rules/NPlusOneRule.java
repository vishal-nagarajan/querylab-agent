package in.utilhub.querylab.rules;

import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The first and only rule in v0.
 * <p>
 * Heuristic: if a single test method emits the same SQL fingerprint more than {@link #threshold()}
 * times, it's almost certainly an N+1 — fanning out one query per loop iteration. We exclude SELECT
 * * style statements only by exclusion of obviously-bulk DML (INSERT/UPDATE/DELETE) so that
 * batch-write tests don't false-positive.
 * <p>
 * Future: tune threshold per fingerprint type, exclude statements emitted as part of a recognised
 * batch insert, support overrides via @QuerylabExpect annotations.
 */
public class NPlusOneRule implements Rule {

    private final int threshold;

    public NPlusOneRule() {
        this(5);
    }

    public NPlusOneRule(int threshold) {
        this.threshold = threshold;
    }

    public int threshold() {
        return threshold;
    }

    @Override
    public String id() {
        return "n_plus_one";
    }

    @Override
    public List<Flag> evaluate(List<Fingerprint> fingerprints,
                               Map<String, Map<String, Integer>> emissionsByTest) {
        Map<String, Fingerprint> byHash = new HashMap<>();
        for (Fingerprint f : fingerprints) {
            byHash.put(f.hash(), f);
        }

        List<Flag> flags = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> testEntry : emissionsByTest.entrySet()) {
            String testName = testEntry.getKey();
            for (Map.Entry<String, Integer> per : testEntry.getValue().entrySet()) {
                int count = per.getValue();
                if (count <= threshold) continue;

                Fingerprint fp = byHash.get(per.getKey());
                if (fp == null) continue;
                if (looksLikeBulkWrite(fp.sql())) continue;

                String suggested = "Consider @EntityGraph, JOIN FETCH, or @BatchSize on the parent association so this fingerprint emits once instead of " + count + "×.";
                String message = "fingerprint emits " + count + "× per call (threshold " + threshold + ")";
                flags.add(new Flag(id(), Flag.Severity.BAD, fp.hash(), testName, message, suggested));
            }
        }
        return flags;
    }

    private static boolean looksLikeBulkWrite(String sql) {
        if (sql == null) return false;
        String head = sql.trim().toLowerCase().split("\\s+", 2)[0];
        return head.equals("insert") || head.equals("update") || head.equals("delete") || head.equals("merge");
    }
}
