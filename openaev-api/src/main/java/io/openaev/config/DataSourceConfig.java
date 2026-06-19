package io.openaev.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Wraps the actual HikariCP DataSource with a {@link LazyConnectionDataSourceProxy} so that JDBC
 * connections are not acquired from the pool until the first SQL statement is executed. This also
 * defers the {@code BEGIN} (setAutoCommit=false) applied by Spring's transaction manager.
 *
 * <p>Benefits:
 *
 * <ul>
 *   <li>Reduces connection pool pressure for transactions that short-circuit before any DB access
 *   <li>Avoids holding idle connections during pre-query business logic
 * </ul>
 */
@Configuration
public class DataSourceConfig {

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  public DataSource actualDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().build();
  }

  @Bean
  @Primary
  public DataSource dataSource(@Qualifier("actualDataSource") DataSource actualDataSource) {
    return new LazyConnectionDataSourceProxy(actualDataSource);
  }
}
