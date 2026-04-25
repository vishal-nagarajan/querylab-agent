package in.utilhub.querylab.explain;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Connects to the user's DB and runs {@code EXPLAIN} for each native query. We do not run
 * {@code EXPLAIN ANALYZE} — pure plan-only, zero execution side effects, safe against any
 * non-prod DB.
 *
 * Bind parameters: queries with {@code ?} placeholders are inlined with {@code NULL} for the plan.
 * That gives a worst-case plan and is good enough to flag "this query has no useful access path."
 * Genuine plan-cache pollution (variable-shape SQL) is out-of-scope here — that's probe mode.
 */
public final class ExplainRunner {

    private final Consumer<String> debug;

    public ExplainRunner(Consumer<String> debug) {
        this.debug = debug;
    }

    /**
     * Returns a map of {@link ExtractedQuery} → plan-text. Queries that fail to EXPLAIN
     * (unsupported dialect, prepared-statement errors, JPQL not native) are skipped silently;
     * the caller can compare keyset to the input to know what was missed.
     */
    public Map<ExtractedQuery, String> explainAll(DataSourceConfig cfg, List<ExtractedQuery> queries) {
        Map<ExtractedQuery, String> out = new LinkedHashMap<>();
        if (queries.isEmpty()) return out;

        DataSourceConfig.Dialect dialect = cfg.dialect();
        if (dialect == DataSourceConfig.Dialect.UNKNOWN) {
            debug.accept("explain: unknown JDBC dialect for " + cfg.url() + " — nothing to do");
            return out;
        }

        Properties props = new Properties();
        if (cfg.username() != null) props.setProperty("user", cfg.username());
        if (cfg.password() != null) props.setProperty("password", cfg.password());

        try (Connection conn = DriverManager.getConnection(cfg.url(), props)) {
            conn.setReadOnly(true);
            for (ExtractedQuery q : queries) {
                if (!q.nativeQuery()) {
                    debug.accept("explain: skipping JPQL query " + q.site() + " (v0.2 supports native only)");
                    continue;
                }
                String sql = inlineNullsForPlaceholders(q.sql());
                String plan = explain(conn, dialect, sql);
                if (plan != null) out.put(q, plan);
            }
        } catch (SQLException e) {
            debug.accept("explain: connection error to " + cfg.url() + " — " + e.getMessage());
        }
        return out;
    }

    private String explain(Connection conn, DataSourceConfig.Dialect dialect, String sql) {
        String prefixed = prefixForDialect(dialect, sql);
        if (prefixed == null) return null;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(prefixed)) {
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                sb.append(rs.getString(1)).append('\n');
            }
            return sb.toString();
        } catch (SQLException e) {
            debug.accept("explain failed for sql=[" + truncate(sql) + "]: " + e.getMessage());
            return null;
        }
    }

    private static String prefixForDialect(DataSourceConfig.Dialect d, String sql) {
        switch (d) {
            case POSTGRES: return "EXPLAIN " + sql;
            case H2:       return "EXPLAIN " + sql;
            case MYSQL:    return "EXPLAIN " + sql;
            default:       return null;
        }
    }

    /**
     * Replace bind placeholders with NULL for plan-only execution. Handles:
     *   {@code ?}       — JDBC-style positional
     *   {@code ?1, ?2…} — Spring Data positional
     *   {@code :name}   — JPA/Hibernate named parameter (skipping {@code ::cast} for Postgres)
     * String literals and double-quoted identifiers are passed through untouched.
     */
    static String inlineNullsForPlaceholders(String sql) {
        StringBuilder sb = new StringBuilder(sql.length());
        boolean inSingle = false, inDouble = false;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (!inDouble && c == '\'') { inSingle = !inSingle; sb.append(c); i++; continue; }
            if (!inSingle && c == '"')  { inDouble = !inDouble; sb.append(c); i++; continue; }
            if (inSingle || inDouble)   { sb.append(c); i++; continue; }

            if (c == '?') {
                int j = i + 1;
                while (j < sql.length() && Character.isDigit(sql.charAt(j))) j++;
                sb.append("NULL");
                i = j;
                continue;
            }
            if (c == ':') {
                // Skip Postgres :: cast operator
                if (i + 1 < sql.length() && sql.charAt(i + 1) == ':') {
                    sb.append("::"); i += 2; continue;
                }
                if (i + 1 < sql.length() && Character.isJavaIdentifierStart(sql.charAt(i + 1))) {
                    int j = i + 1;
                    while (j < sql.length() && Character.isJavaIdentifierPart(sql.charAt(j))) j++;
                    sb.append("NULL");
                    i = j;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String truncate(String s) {
        return s.length() < 80 ? s : s.substring(0, 77) + "...";
    }
}
