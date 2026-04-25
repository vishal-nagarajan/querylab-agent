package in.utilhub.querylab.baseline;

import in.utilhub.querylab.model.Fingerprint;
import in.utilhub.querylab.model.Flag;
import in.utilhub.querylab.model.RunReport;

import java.util.ArrayList;
import java.util.List;

/**
 * "What's new since baseline?" Filters a fresh {@link RunReport} against a {@link BaselineSnapshot}
 * and surfaces the deltas. Intended for the per-PR review surface — show what this branch added,
 * not the full inventory.
 */
public final class BaselineDiff {

    private final List<Fingerprint> newFingerprints;
    private final List<Flag> newFlags;
    private final int approvedFingerprintsHidden;
    private final int approvedFlagsHidden;

    private BaselineDiff(List<Fingerprint> newFingerprints, List<Flag> newFlags,
                          int approvedFingerprintsHidden, int approvedFlagsHidden) {
        this.newFingerprints = newFingerprints;
        this.newFlags = newFlags;
        this.approvedFingerprintsHidden = approvedFingerprintsHidden;
        this.approvedFlagsHidden = approvedFlagsHidden;
    }

    public static BaselineDiff compute(RunReport current, BaselineSnapshot baseline) {
        List<Fingerprint> newFps = new ArrayList<>();
        int hiddenFps = 0;
        for (Fingerprint f : current.fingerprints()) {
            if (baseline.knowsFingerprint(f.hash())) hiddenFps++;
            else newFps.add(f);
        }
        List<Flag> newFlags = new ArrayList<>();
        int hiddenFlags = 0;
        for (Flag fl : current.flags()) {
            if (baseline.knowsFlag(fl.ruleId(), fl.fingerprintHash(), fl.testMethod())) hiddenFlags++;
            else newFlags.add(fl);
        }
        return new BaselineDiff(newFps, newFlags, hiddenFps, hiddenFlags);
    }

    public List<Fingerprint> newFingerprints()       { return newFingerprints; }
    public List<Flag> newFlags()                     { return newFlags; }
    public int approvedFingerprintsHidden()          { return approvedFingerprintsHidden; }
    public int approvedFlagsHidden()                 { return approvedFlagsHidden; }
    public boolean isClean()                         { return newFlags.isEmpty(); }
    public boolean isEmpty()                         { return newFingerprints.isEmpty() && newFlags.isEmpty(); }
}
