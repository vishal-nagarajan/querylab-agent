package in.utilhub.querylab.model;

/**
 * A finding raised by a Rule against the run. Severity drives the report colour.
 */
public final class Flag {
    public enum Severity { INFO, WARN, BAD }

    private final String ruleId;
    private final Severity severity;
    private final String fingerprintHash;
    private final String testMethod;
    private final String message;
    private final String suggestedFix;

    public Flag(String ruleId, Severity severity, String fingerprintHash,
                String testMethod, String message, String suggestedFix) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.fingerprintHash = fingerprintHash;
        this.testMethod = testMethod;
        this.message = message;
        this.suggestedFix = suggestedFix;
    }

    public String ruleId()           { return ruleId; }
    public Severity severity()       { return severity; }
    public String fingerprintHash()  { return fingerprintHash; }
    public String testMethod()       { return testMethod; }
    public String message()          { return message; }
    public String suggestedFix()     { return suggestedFix; }
}
