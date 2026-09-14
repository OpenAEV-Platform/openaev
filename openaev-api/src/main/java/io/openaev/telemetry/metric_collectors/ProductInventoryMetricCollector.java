package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.NotificationTriggerRepository;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.ReportingRepository;
import io.openaev.database.repository.VulnerabilityRepository;
import io.openaev.database.repository.VulnerableEndpointRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Product adoption inventory: snapshot gauges of the main content and configuration objects, for
 * product management and leadership analytics. Anonymous only: type/source/status enums and counts,
 * never any object content.
 *
 * <p>Tenant scoping: every gauge here is intentionally platform-wide (telemetry is per-instance,
 * not per-tenant), like {@code total_users_count} in {@link GlobalMetricCollector}. How that is
 * achieved depends on which isolation the counted table is on, and the difference is easy to miss.
 *
 * <p>For a table still on v1, it comes for free: the suppliers run on the OpenTelemetry exporter
 * thread outside any {@code @Transactional} method, so the Hibernate {@code tenantFilter} is never
 * enabled (it is only turned on by {@code HibernateFilterTransactionAspect} on method-level
 * {@code @Transactional} executions) and the count spans every tenant.
 *
 * <p>For a table in {@code openaev.tenant.active-tables} that reasoning does NOT hold. {@code
 * TenantStatementInspector} does not care whether a transaction is active: with {@code
 * app.current_tenants} unset, {@code can_access_tenant} returns false and the count is silently
 * ZERO. Such a gauge must open an explicit {@code TxCtx.allTenants()} scope through {@link
 * TenantScopedTransaction}, as {@link #countAssetGroups()} does.
 *
 * <p>{@link #collectSecurityPlatforms()} and {@link #collectEndpoints()} carry the same scope for
 * the same reason: both count rows of the {@code assets} table (#6438).
 *
 * <p>{@link #countFindings()} carries it too, for the same reason (#6420). The same applies to any
 * other gauge here whose table joins a future activation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductInventoryMetricCollector {

  private final MetricRegistry metricRegistry;
  private final TenantScopedTransaction tenantTx;
  private final AssetGroupRepository assetGroupRepository;
  private final OrganizationRepository organizationRepository;
  private final InjectRepository injectRepository;
  private final ChallengeRepository challengeRepository;
  private final DocumentRepository documentRepository;
  private final ChannelRepository channelRepository;
  private final ArticleRepository articleRepository;
  private final CustomDashboardRepository customDashboardRepository;
  private final ReportingRepository reportingRepository;
  private final ImportMapperRepository importMapperRepository;
  private final NotificationTriggerRepository notificationTriggerRepository;
  private final WorkflowRepository workflowRepository;
  private final FindingRepository findingRepository;
  private final VulnerabilityRepository vulnerabilityRepository;
  private final VulnerableEndpointRepository vulnerableEndpointRepository;
  private final AttackPatternRepository attackPatternRepository;

  @PersistenceContext private EntityManager entityManager;

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "payloads_total",
        "Payloads broken down by type, source (MANUAL/FILIGRAN/COMMUNITY) and status",
        this::collectPayloads);
    metricRegistry.registerMultiGauge(
        "teams_total", "Teams broken down by contextual flag", this::collectTeams);
    metricRegistry.registerMultiGauge(
        "endpoints_total", "Endpoints broken down by platform", this::collectEndpoints);
    metricRegistry.registerGauge(
        "scenarios_recurring_total",
        "Number of scenarios with a scheduled recurrence",
        () -> safeCount(this::countRecurringScenarios));
    metricRegistry.registerGauge(
        "asset_groups_total", "Number of asset groups", () -> safeCount(this::countAssetGroups));
    metricRegistry.registerMultiGauge(
        "security_platforms_total",
        "Security platforms broken down by type (EDR, XDR, SIEM, ...)",
        this::collectSecurityPlatforms);
    metricRegistry.registerGauge(
        "organizations_total",
        "Number of organizations",
        () -> safeCount(organizationRepository::count));
    metricRegistry.registerGauge(
        "injects_total", "Number of injects", () -> safeCount(injectRepository::count));
    metricRegistry.registerGauge(
        "challenges_total", "Number of challenges", () -> safeCount(this::countChallenges));
    metricRegistry.registerGauge(
        "documents_total", "Number of documents", () -> safeCount(documentRepository::count));
    metricRegistry.registerGauge(
        "channels_total", "Number of media channels", () -> safeCount(this::countChannels));
    metricRegistry.registerGauge(
        "articles_total", "Number of media articles", () -> safeCount(articleRepository::count));
    metricRegistry.registerGauge(
        "custom_dashboards_total",
        "Number of custom dashboards",
        () -> safeCount(this::countCustomDashboards));
    metricRegistry.registerGauge(
        "reports_total", "Number of reports", () -> safeCount(reportingRepository::count));
    metricRegistry.registerGauge(
        "mappers_total", "Number of XLS import mappers", () -> safeCount(this::countImportMappers));
    metricRegistry.registerGauge(
        "notification_triggers_total",
        "Number of notification triggers",
        () -> safeCount(this::countNotificationTriggers));
    metricRegistry.registerGauge(
        "workflows_total",
        "Number of chaining workflows",
        () -> safeCount(workflowRepository::count));
    metricRegistry.registerGauge(
        "findings_total", "Number of findings", () -> safeCount(this::countFindings));
    metricRegistry.registerGauge(
        "vulnerabilities_total",
        "Number of vulnerabilities",
        () -> safeCount(vulnerabilityRepository::count));
    metricRegistry.registerGauge(
        "vulnerable_endpoints_total",
        "Number of vulnerable endpoints",
        () -> safeCount(this::countVulnerableEndpoints));
    metricRegistry.registerGauge(
        "attack_patterns_total",
        "Number of attack patterns",
        () -> safeCount(attackPatternRepository::count));
  }

  private Map<Attributes, Long> collectPayloads() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      List<Object[]> rows =
          entityManager
              .createQuery(
                  "select p.type, p.source, p.status, count(p) from Payload p"
                      + " group by p.type, p.source, p.status",
                  Object[].class)
              .getResultList();
      for (Object[] row : rows) {
        Attributes attributes =
            Attributes.of(
                stringKey("type"), normalizeEnumLabel(row[0]),
                stringKey("source"), normalizeEnumLabel(row[1]),
                stringKey("status"), normalizeEnumLabel(row[2]));
        result.merge(attributes, (Long) row[3], Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect payload inventory", e);
    }
    return result;
  }

  private Map<Attributes, Long> collectTeams() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      List<Object[]> rows =
          entityManager
              .createQuery(
                  "select t.contextual, count(t) from Team t group by t.contextual", Object[].class)
              .getResultList();
      for (Object[] row : rows) {
        boolean contextual = Boolean.TRUE.equals(row[0]);
        result.merge(Attributes.of(booleanKey("contextual"), contextual), (Long) row[1], Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect team inventory", e);
    }
    return result;
  }

  /** Package-private: the tenant-scope regression test calls it without the OTel plumbing. */
  Map<Attributes, Long> collectSecurityPlatforms() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      // SecurityPlatform is a row of the v2-active assets table, so this platform-wide gauge needs
      // the explicit all-tenants scope like countAssetGroups; without it the count is silently
      // zero.
      List<Object[]> rows =
          tenantTx.execute(
              TxCtx.allTenants(),
              () ->
                  entityManager
                      .createQuery(
                          "select sp.securityPlatformType, count(sp) from SecurityPlatform sp"
                              + " group by sp.securityPlatformType",
                          Object[].class)
                      .getResultList());
      for (Object[] row : rows) {
        result.merge(
            Attributes.of(stringKey("type"), normalizeEnumLabel(row[0])), (Long) row[1], Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect security platform inventory", e);
    }
    return result;
  }

  /** Package-private: the tenant-scope regression test calls it without the OTel plumbing. */
  Map<Attributes, Long> collectEndpoints() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      // Endpoint is a row of the v2-active assets table: same explicit scope as above.
      List<Object[]> rows =
          tenantTx.execute(
              TxCtx.allTenants(),
              () ->
                  entityManager
                      .createQuery(
                          "select e.platform, count(e) from Endpoint e group by e.platform",
                          Object[].class)
                      .getResultList());
      for (Object[] row : rows) {
        result.merge(
            Attributes.of(stringKey("platform"), normalizeEnumLabel(row[0])),
            (Long) row[1],
            Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect endpoint inventory", e);
    }
    return result;
  }

  /**
   * Platform-wide on purpose, like every gauge here, and explicitly scoped because {@code findings}
   * is v2-active: with {@code app.current_tenants} unset the inspector makes {@code
   * can_access_tenant} false for every row and the count is silently ZERO. Same treatment as {@link
   * #countAssetGroups()} (#6420).
   */
  long countFindings() {
    return tenantTx.<Long>execute(TxCtx.allTenants(), () -> findingRepository.count());
  }

  private long countRecurringScenarios() {
    return entityManager
        .createQuery(
            "select count(s) from Scenario s where s.recurrence is not null and s.recurrence <> ''",
            Long.class)
        .getSingleResult();
  }

  /**
   * Counts asset groups across the whole platform.
   *
   * <p>{@code asset_groups} is v2-active, so this count MUST carry an explicit scope: {@code
   * can_access_tenant} is fail-closed and an unscoped count returns zero, not an error. {@code
   * allTenants()} is the scope that matches this gauge's stated intention (platform-wide
   * telemetry), and it is resolved into an explicit list of live tenants, never a wildcard.
   */
  // VulnerableEndpointRepository is a JpaRepository<Endpoint, String> and Endpoint is a
  // discriminator on the activated assets table, so an unscoped count() is filtered by
  // can_access_tenant against an empty scope and reports zero.
  long countVulnerableEndpoints() {
    return countAcrossAllTenants(vulnerableEndpointRepository::count);
  }

  long countAssetGroups() {
    return countAcrossAllTenants(assetGroupRepository::count);
  }

  /** Counts channels across the whole platform (channels is v2-active). */
  long countChannels() {
    return countAcrossAllTenants(channelRepository::count);
  }

  /** Counts challenges across the whole platform (challenges is v2-active, #6416). */
  long countChallenges() {
    return countAcrossAllTenants(challengeRepository::count);
  }

  /**
   * Counts notification triggers across the whole platform (notification_triggers is v2-active).
   */
  long countNotificationTriggers() {
    return countAcrossAllTenants(notificationTriggerRepository::count);
  }

  /** Counts XLS import mappers across the whole platform (import_mappers is v2-active). */
  long countImportMappers() {
    return countAcrossAllTenants(importMapperRepository::count);
  }

  /** Counts custom dashboards across the whole platform (custom_dashboards is v2-active). */
  long countCustomDashboards() {
    return countAcrossAllTenants(customDashboardRepository::count);
  }

  private long countAcrossAllTenants(Supplier<Long> counter) {
    // Explicit type witness: TenantScopedTransaction overloads execute() on Supplier and
    // Runnable, so a value-returning method reference is ambiguous without it.
    return tenantTx.<Long>execute(TxCtx.allTenants(), counter);
  }

  private long safeCount(Supplier<Long> counter) {
    try {
      return counter.get();
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect inventory count", e);
      return 0L;
    }
  }

  /** Enum-backed label value: NULL columns become {@code unknown} instead of the literal "null". */
  private static String normalizeEnumLabel(Object value) {
    return MetricRegistry.normalizeLabel(value == null ? null : value.toString());
  }
}
