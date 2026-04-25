package in.utilhub.querylab.model;

/**
 * One execution of a SQL statement during a test. Lightweight by design — we hold many of these.
 */
public final class Emission {
    private final String fingerprintHash;
    private final long sequence;

    public Emission(String fingerprintHash, long sequence) {
        this.fingerprintHash = fingerprintHash;
        this.sequence = sequence;
    }

    public String fingerprintHash() {
        return fingerprintHash;
    }

    public long sequence() {
        return sequence;
    }
}
