package in.utilhub.querylab.baseline;

import java.util.Collections;
import java.util.Set;

/**
 * The minimal facts we need from a previously-saved {@code .querylab/baseline.json} so we can
 * decide which findings are NEW on the current run vs. already approved.
 */
public final class BaselineSnapshot {

    public static final BaselineSnapshot EMPTY = new BaselineSnapshot(
        Collections.emptySet(),
        Collections.emptySet()
    );

    private final Set<String> fingerprintHashes;
    /** Flag identity: {@code ruleId|fingerprintHash|testMethod}. */
    private final Set<String> flagKeys;

    public BaselineSnapshot(Set<String> fingerprintHashes, Set<String> flagKeys) {
        this.fingerprintHashes = fingerprintHashes;
        this.flagKeys = flagKeys;
    }

    public boolean knowsFingerprint(String hash) {
        return fingerprintHashes.contains(hash);
    }

    public boolean knowsFlag(String ruleId, String fingerprintHash, String testMethod) {
        return flagKeys.contains(flagKey(ruleId, fingerprintHash, testMethod));
    }

    public boolean isEmpty() {
        return fingerprintHashes.isEmpty() && flagKeys.isEmpty();
    }

    public int knownFingerprintCount() { return fingerprintHashes.size(); }
    public int knownFlagCount()        { return flagKeys.size(); }

    public static String flagKey(String ruleId, String fingerprintHash, String testMethod) {
        return ruleId + "|" + fingerprintHash + "|" + testMethod;
    }
}
