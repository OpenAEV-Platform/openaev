package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

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
 * not per-tenant), like {@code total_users_count} in {@link GlobalMetricCollector}. All suppliers
 * run on the OpenTelemetry exporter thread outside any {@code @Transactional} method, so the
 * Hibernate {@code tenantFilter} is never enabled (it is only turned on by {@code
 * HibernateFilterTransactionAspect} on method-level {@code @Transactional} executions): both the
 * JPQL queries and the Spring Data {@code count()} calls therefore consistently count across all
 * tenants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductInventoryMetricCollector {

  private final MetricRegistry metricRegistry;
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
        "asset_groups_total",
        "Number of asset groups",
        () -> safeCount(assetGroupRepository::count));
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
        "challenges_total", "Number of challenges", () -> safeCount(challengeRepository::count));
    metricRegistry.registerGauge(
        "documents_total", "Number of documents", () -> safeCount(documentRepository::count));
    metricRegistry.registerGauge(
        "channels_total", "Number of media channels", () -> safeCount(channelRepository::count));
    metricRegistry.registerGauge(
        "articles_total", "Number of media articles", () -> safeCount(articleRepository::count));
    metricRegistry.registerGauge(
        "custom_dashboards_total",
        "Number of custom dashboards",
        () -> safeCount(customDashboardRepository::count));
    metricRegistry.registerGauge(
        "reports_total", "Number of reports", () -> safeCount(reportingRepository::count));
    metricRegistry.registerGauge(
        "mappers_total",
        "Number of XLS import mappers",
        () -> safeCount(importMapperRepository::count));
    metricRegistry.registerGauge(
        "notification_triggers_total",
        "Number of notification triggers",
        () -> safeCount(notificationTriggerRepository::count));
    metricRegistry.registerGauge(
        "workflows_total",
        "Number of chaining workflows",
        () -> safeCount(workflowRepository::count));
    metricRegistry.registerGauge(
        "findings_total", "Number of findings", () -> safeCount(findingRepository::count));
    metricRegistry.registerGauge(
        "vulnerabilities_total",
        "Number of vulnerabilities",
        () -> safeCount(vulnerabilityRepository::count));
    metricRegistry.registerGauge(
        "vulnerable_endpoints_total",
        "Number of vulnerable endpoints",
        () -> safeCount(vulnerableEndpointRepository::count));
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

  private Map<Attributes, Long> collectSecurityPlatforms() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      List<Object[]> rows =
          entityManager
              .createQuery(
                  "select sp.securityPlatformType, count(sp) from SecurityPlatform sp"
                      + " group by sp.securityPlatformType",
                  Object[].class)
              .getResultList();
      for (Object[] row : rows) {
        result.merge(
            Attributes.of(stringKey("type"), normalizeEnumLabel(row[0])), (Long) row[1], Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect security platform inventory", e);
    }
    return result;
  }

  private Map<Attributes, Long> collectEndpoints() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      List<Object[]> rows =
          entityManager
              .createQuery(
                  "select e.platform, count(e) from Endpoint e group by e.platform", Object[].class)
              .getResultList();
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

  private long countRecurringScenarios() {
    return entityManager
        .createQuery(
            "select count(s) from Scenario s where s.recurrence is not null and s.recurrence <> ''",
            Long.class)
        .getSingleResult();
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
