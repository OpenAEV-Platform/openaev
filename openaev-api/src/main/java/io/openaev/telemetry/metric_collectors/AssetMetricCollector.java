package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Telemetry on the asset inventory of the platform: assets broken down by category (HOST,
 * CLOUD_RESOURCE, WEB_APPLICATION, ...) and agent coverage (agent based when at least one agent is
 * installed on the asset, agentless otherwise). Counts only, no asset content is ever collected.
 *
 * <p>The counts are intentionally instance-wide (all tenants): the query runs on the OTel exporter
 * thread, outside any request or tenant context. Note for multi-tenancy v2: if the {@code assets}
 * or {@code agents} tables are ever activated in {@code openaev.tenant.active-tables}, the
 * fail-closed {@link io.openaev.config.TenantStatementInspector} will reject this query on a thread
 * without a tenant scope and the gauge will degrade to empty (error logged); the collector must
 * then move to an inspector-bypassing path (e.g. raw JDBC) to stay instance-wide.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMetricCollector {

  private static final String ATTRIBUTE_CATEGORY = "category";
  private static final String ATTRIBUTE_AGENT_COVERAGE = "agent_coverage";
  private static final String AGENT_BASED = "agent_based";
  private static final String AGENTLESS = "agentless";

  private final MetricRegistry metricRegistry;

  @PersistenceContext private EntityManager entityManager;

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "assets_total",
        "Assets broken down by category and agent coverage (agent_based/agentless)",
        this::collectAssets);
  }

  private Map<Attributes, Long> collectAssets() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      @SuppressWarnings("unchecked")
      List<Object[]> rows =
          entityManager
              .createNativeQuery(
                  "select t.asset_category, t.has_agent, count(*) from ("
                      + "  select a.asset_category,"
                      + "    exists (select 1 from agents ag where ag.agent_asset = a.asset_id) as has_agent"
                      + "  from assets a"
                      + ") t group by 1, 2")
              .getResultList();
      for (Object[] row : rows) {
        Attributes attributes =
            Attributes.of(
                stringKey(ATTRIBUTE_CATEGORY),
                MetricRegistry.normalizeLabel(row[0] == null ? null : row[0].toString()),
                stringKey(ATTRIBUTE_AGENT_COVERAGE),
                Boolean.TRUE.equals(row[1]) ? AGENT_BASED : AGENTLESS);
        result.merge(attributes, ((Number) row[2]).longValue(), Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect asset inventory metrics", e);
    }
    return result;
  }
}
