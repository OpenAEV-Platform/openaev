package io.openaev.service;

import io.openaev.config.EngineConfig;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.driver.ElasticDriver;
import io.openaev.driver.OpenSearchDriver;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

/**
 * Factory component for creating the appropriate search engine service.
 *
 * <p>This component is responsible for instantiating either an {@link ElasticService} or {@link
 * OpenSearchService} based on the configured engine selector. The created service is registered as
 * a Spring bean.
 *
 * <p>Supported engine selectors:
 *
 * <ul>
 *   <li>{@code elk} - Elasticsearch
 *   <li>{@code opensearch} - OpenSearch
 * </ul>
 *
 * @see EngineConfig
 * @see ElasticService
 * @see OpenSearchService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EngineComponent {

  private final EngineConfig config;
  private final EngineContext searchEngine;
  private final OpenSearchDriver openSearchDriver;
  private final ElasticDriver elasticDriver;
  private final IndexingStatusRepository indexingStatusRepository;
  private final CommonSearchService commonSearchService;

  /**
   * Creates and configures the search engine service based on configuration.
   *
   * @return the configured {@link EngineService} implementation
   * @throws Exception if there is an issue during engine initialization
   * @throws IllegalStateException if the engine selector is not supported
   */
  @Bean
  public EngineService engine() throws Exception {
    if (config.getEngineSelector().equalsIgnoreCase("elk")) {
      return new ElasticService(
          searchEngine, elasticDriver, indexingStatusRepository, config, commonSearchService);
    }
    if (config.getEngineSelector().equalsIgnoreCase("opensearch")) {
      return new OpenSearchService(
          searchEngine, openSearchDriver, indexingStatusRepository, config, commonSearchService);
    }
    throw new IllegalStateException("engine selector not supported");
  }
}
