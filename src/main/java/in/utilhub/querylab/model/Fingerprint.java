package in.utilhub.querylab.model;

/**
 * A distinct SQL shape captured during the run. One Fingerprint per unique SQL template.
 */
public final class Fingerprint {
    private final String hash;          // sha256 of normalized template
    private final String sql;           // template text (with ? placeholders)
    private final int totalEmissions;   // how many times this fired across the whole run
    private final int distinctTests;    // how many test methods fired this

    public Fingerprint(String hash, String sql, int totalEmissions, int distinctTests) {
        this.hash = hash;
        this.sql = sql;
        this.totalEmissions = totalEmissions;
        this.distinctTests = distinctTests;
    }

    public String hash()           { return hash; }
    public String sql()            { return sql; }
    public int totalEmissions()    { return totalEmissions; }
    public int distinctTests()     { return distinctTests; }
}
