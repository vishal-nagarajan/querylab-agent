package in.utilhub.querylab.explain;

/**
 * One {@code @Query} annotation harvested from a Spring Data repository. The agent doesn't run
 * these — it just feeds them through EXPLAIN so we can see how the database plans them.
 */
public final class ExtractedQuery {
    private final String repositoryClass;   // dotted class name
    private final String methodName;
    private final String sql;
    private final boolean nativeQuery;

    public ExtractedQuery(String repositoryClass, String methodName, String sql, boolean nativeQuery) {
        this.repositoryClass = repositoryClass;
        this.methodName = methodName;
        this.sql = sql;
        this.nativeQuery = nativeQuery;
    }

    public String repositoryClass() { return repositoryClass; }
    public String methodName()      { return methodName; }
    public String sql()             { return sql; }
    public boolean nativeQuery()    { return nativeQuery; }

    /** Stable site identifier used in flag attribution. */
    public String site() {
        return repositoryClass + "#" + methodName;
    }

    @Override
    public String toString() {
        return site() + (nativeQuery ? " [native] " : " [jpql] ") + sql;
    }
}
