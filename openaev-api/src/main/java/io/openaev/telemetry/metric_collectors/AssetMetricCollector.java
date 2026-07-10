package io.openaev.telemetry.metric_collectors;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Telemetry on the asset coverage of the platform: how many assets (endpoints) exist and how many
 * of them are agent based (at least one agent installed) versus agentless. Counts only, no asset
 * content is ever collected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMetricCollector {

  private final MetricRegistry metricRegistry;

  @PersistenceContext private EntityManager entityManager;

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "total_assets_count",
        "Number of assets (endpoints), agent based or agentless",
        () -> safeCount("select count(e) from Endpoint e"));
    metricRegistry.registerGauge(
        "total_agent_based_assets_count",
        "Number of assets (endpoints) with at least one agent installed",
        () -> safeCount("select count(e) from Endpoint e where e.agents is not empty"));
    metricRegistry.registerGauge(
        "total_agentless_assets_count",
        "Number of assets (endpoints) without any agent installed",
        () -> safeCount("select count(e) from Endpoint e where e.agents is empty"));
  }

  private long safeCount(String jpql) {
    try {
      return entityManager.createQuery(jpql, Long.class).getSingleResult();
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect asset coverage metrics", e);
      return 0;
    }
  }
}
