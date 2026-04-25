package in.utilhub.querylab.rules;

import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;

import java.util.List;
import java.util.Map;

/**
 * A predicate over the captured run that emits zero or more Flags.
 * v0 ships one rule. v0.1+ adds the rest from CLAUDE.md's seed list.
 */
public interface Rule {
    String id();

    List<Flag> evaluate(List<Fingerprint> fingerprints,
                        Map<String, Map<String, Integer>> emissionsByTest);
}
