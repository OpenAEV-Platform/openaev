package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
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
 * thread, outside any request or tenant context. Under v1 that came for free, because the Hibernate
 * {@code tenantFilter} is only enabled on a transactional call. {@code assets} is on v2, and {@link
 * io.openaev.config.TenantStatementInspector} does not care whether a transaction is active: with
 * {@code app.current_tenants} unset, {@code can_access_tenant} returns false for every row and the
 * gauge reports an empty map. It keeps reporting, it just reports nothing.
 *
 * <p>The scope is therefore explicit, {@code TxCtx.allTenants()} through {@link
 * TenantScopedTransaction}. An earlier version of this javadoc prescribed raw JDBC to bypass the
 * inspector instead. That advice predates the primitive and would trade a documented, enumerated
 * cross-tenant intention for an unreviewable one, which is what the {@code
 * TenantNonOrmAccessArchTest} guardrail exists to prevent.
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
  private final TenantScopedTransaction tenantTx;

  @PersistenceContext private EntityManager entityManager;

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "assets_total",
        "Assets broken down by category and agent coverage (agent_based/agentless)",
        this::collectAssets);
  }

  /** Package-private so the tenant-scope regression test can call it without the OTel plumbing. */
  Map<Attributes, Long> collectAssets() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      @SuppressWarnings("unchecked")
      List<Object[]> rows =
          tenantTx.execute(
              TxCtx.allTenants(),
              () ->
                  entityManager
                      .createNativeQuery(
                          "select t.asset_category, t.has_agent, count(*) from ("
                              + "  select a.asset_category,"
                              + "    exists (select 1 from agents ag where ag.agent_asset ="
                              + " a.asset_id) as has_agent"
                              + "  from assets a"
                              + ") t group by 1, 2")
                      .getResultList());
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
