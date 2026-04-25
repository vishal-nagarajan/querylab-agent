package in.utilhub.querylab.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The whole run, ready for serialisation. Built by QueryLab at end-of-plan.
 */
public final class RunReport {
    private final Instant capturedAt;
    private final long totalEmissions;
    private final int distinctFingerprints;
    private final int testsExecuted;
    private final List<Fingerprint> fingerprints;
    /** test method → fingerprint hash → emission count within that test */
    private final Map<String, Map<String, Integer>> emissionsByTest;
    private final List<Flag> flags;

    public RunReport(Instant capturedAt, long totalEmissions, int distinctFingerprints,
                     int testsExecuted, List<Fingerprint> fingerprints,
                     Map<String, Map<String, Integer>> emissionsByTest, List<Flag> flags) {
        this.capturedAt = capturedAt;
        this.totalEmissions = totalEmissions;
        this.distinctFingerprints = distinctFingerprints;
        this.testsExecuted = testsExecuted;
        this.fingerprints = Collections.unmodifiableList(fingerprints);
        this.emissionsByTest = Collections.unmodifiableMap(emissionsByTest);
        this.flags = Collections.unmodifiableList(flags);
    }

    public Instant capturedAt()                            { return capturedAt; }
    public long totalEmissions()                           { return totalEmissions; }
    public int distinctFingerprints()                      { return distinctFingerprints; }
    public int testsExecuted()                             { return testsExecuted; }
    public List<Fingerprint> fingerprints()                { return fingerprints; }
    public Map<String, Map<String, Integer>> emissionsByTest() { return emissionsByTest; }
    public List<Flag> flags()                              { return flags; }
}
