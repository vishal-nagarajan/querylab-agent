package in.utilhub.querylab.capture;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate's pre-execution hook for every SQL statement. Single instance per SessionFactory.
 * <p>
 * We pass the SQL through unchanged and record it under whichever TestScope is currently bound to
 * the calling thread. SQL emitted outside a test (e.g. schema export at context startup) is ignored.
 */
public class QueryCaptureInspector implements StatementInspector {
    private static final long serialVersionUID = 1L;

    @Override
    public String inspect(String sql) {
        try {
            QueryLab.global().recordEmission(TestScope.current(), sql);
        } catch (RuntimeException ignored) {
            // never fail the user's query because of our bookkeeping
        }
        return sql;
    }
}
