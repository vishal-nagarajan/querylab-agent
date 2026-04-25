package in.utilhub.querylab.rules;

import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RuleEngine {
    private final List<Rule> rules = new ArrayList<>();

    public void register(Rule rule) {
        rules.add(rule);
    }

    public List<Flag> runOver(List<Fingerprint> fingerprints,
                              Map<String, Map<String, Integer>> emissionsByTest) {
        List<Flag> all = new ArrayList<>();
        for (Rule r : rules) {
            try {
                all.addAll(r.evaluate(fingerprints, emissionsByTest));
            } catch (RuntimeException e) {
                System.err.println("[querylab] rule " + r.id() + " failed: " + e.getMessage());
            }
        }
        return all;
    }
}
