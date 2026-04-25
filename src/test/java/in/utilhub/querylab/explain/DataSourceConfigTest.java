package in.utilhub.querylab.explain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataSourceConfigTest {

    @Test
    void postgresUrl() {
        assertEquals(DataSourceConfig.Dialect.POSTGRES,
            new DataSourceConfig("jdbc:postgresql://host/db", "u", "p", "param").dialect());
    }

    @Test
    void h2Url() {
        assertEquals(DataSourceConfig.Dialect.H2,
            new DataSourceConfig("jdbc:h2:mem:test", null, null, "param").dialect());
    }

    @Test
    void mysqlUrl() {
        assertEquals(DataSourceConfig.Dialect.MYSQL,
            new DataSourceConfig("jdbc:mysql://host/db", "u", "p", "param").dialect());
    }

    @Test
    void mariadbResolvesAsMysql() {
        // MariaDB uses MySQL-compatible EXPLAIN syntax — we treat it as the same dialect family.
        assertEquals(DataSourceConfig.Dialect.MYSQL,
            new DataSourceConfig("jdbc:mariadb://host/db", "u", "p", "param").dialect());
    }

    @Test
    void oracleUrl() {
        assertEquals(DataSourceConfig.Dialect.ORACLE,
            new DataSourceConfig("jdbc:oracle:thin:@host:1521:xe", "u", "p", "param").dialect());
    }

    @Test
    void unknownUrl() {
        assertEquals(DataSourceConfig.Dialect.UNKNOWN,
            new DataSourceConfig("not-a-jdbc-url", null, null, "param").dialect());
    }
}
