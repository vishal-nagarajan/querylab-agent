package in.utilhub.querylab.explain;

/**
 * The minimal JDBC connection details the EXPLAIN runner needs.
 * Read from a Mojo parameter, or auto-resolved from {@code application.yml}.
 */
public final class DataSourceConfig {
    private final String url;
    private final String username;
    private final String password;
    private final String source;   // "param" | "application.yml" | "application-{profile}.yml" — for diagnostics

    public DataSourceConfig(String url, String username, String password, String source) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.source = source;
    }

    public String url()      { return url; }
    public String username() { return username; }
    public String password() { return password; }
    public String source()   { return source; }

    public Dialect dialect() {
        if (url == null) return Dialect.UNKNOWN;
        if (url.startsWith("jdbc:postgresql:")) return Dialect.POSTGRES;
        if (url.startsWith("jdbc:h2:"))         return Dialect.H2;
        if (url.startsWith("jdbc:mysql:"))      return Dialect.MYSQL;
        if (url.startsWith("jdbc:mariadb:"))    return Dialect.MYSQL;
        if (url.startsWith("jdbc:oracle:"))     return Dialect.ORACLE;
        return Dialect.UNKNOWN;
    }

    public enum Dialect { POSTGRES, H2, MYSQL, ORACLE, UNKNOWN }
}
