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
