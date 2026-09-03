package io.openaev.utilstest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.openaev.driver.ElasticDriver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes an {@link ElasticsearchClient} bean for the test application context.
 *
 * <p>Spring Boot's built-in {@code ElasticsearchClientAutoConfiguration} (previously in {@code
 * org.springframework.boot.autoconfigure.elasticsearch}) was removed in Spring Boot 4.0 - see
 * https://github.com/spring-projects/spring-boot/... (module split away from spring-boot-autoconfigure).
 * Production code never relied on that autoconfigured bean (see {@link ElasticDriver#elasticClient()}
 * / {@code ElasticService}, which build/hold their own client), but {@link DatabaseSnapshotManager}
 * (test-only) does {@code @Autowired} an {@link ElasticsearchClient} directly, expecting Spring to
 * provide one. This configuration restores that bean for tests by delegating to the same connection
 * logic {@code ElasticDriver} already uses in production, instead of duplicating client construction.
 */
@Configuration
@RequiredArgsConstructor
public class ElasticsearchClientTestConfiguration {

  private final ElasticDriver elasticDriver;

  @Bean
  public ElasticsearchClient elasticsearchClient() throws Exception {
    return elasticDriver.elasticClient();
  }
}
