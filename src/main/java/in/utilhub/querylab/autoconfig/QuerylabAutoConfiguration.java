package in.utilhub.querylab.autoconfig;

import in.utilhub.querylab.capture.QueryCaptureInspector;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Wires the {@link QueryCaptureInspector} into Hibernate via Spring Boot.
 *
 * Discovered through META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 * Activates only when both Hibernate and Spring Boot's HibernatePropertiesCustomizer are on the
 * classpath — i.e. a Spring Boot test context with JPA.
 */
@AutoConfiguration
@ConditionalOnClass({StatementInspector.class, HibernatePropertiesCustomizer.class})
public class QuerylabAutoConfiguration {

    @Bean
    public QueryCaptureInspector querylabStatementInspector() {
        return new QueryCaptureInspector();
    }

    @Bean
    public HibernatePropertiesCustomizer querylabHibernateCustomizer(QueryCaptureInspector inspector) {
        return props -> props.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
    }
}
