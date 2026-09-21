package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.context.TenantScopedTransaction;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Telemetry on the number of marking definitions available in the current tenant scope. Unlike
 * platform-wide product metrics, marking definitions are tenant-scoped and must not count rows from
 * every tenant or the default tenant fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarkingMetricCollector {

  private static final String ATTRIBUTE_TENANT_HASH = "tenant_hash";
  private static final String UNKNOWN_TENANT_HASH = "unknown";

  private final MetricRegistry metricRegistry;
  private final TenantScopedTransaction tenantTx;

  @PersistenceContext private EntityManager entityManager;

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "markings_total",
        "Total number of marking definitions in the current tenant scope",
        this::collectMarkings);
  }

  private Map<Attributes, Long> collectMarkings() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      tenantTx.forEachTenant(
          tenantId -> {
            String tenantHash = hashTenantId(tenantId);
            long markingCount = countMarkingsForTenant(tenantId);
            result.put(
                Attributes.builder().put(stringKey(ATTRIBUTE_TENANT_HASH), tenantHash).build(),
                markingCount);
          });
      if (result.isEmpty()) {
        result.put(
            Attributes.builder().put(stringKey(ATTRIBUTE_TENANT_HASH), UNKNOWN_TENANT_HASH).build(),
            0L);
      }
      return result;
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect marking definition metrics", e);
      return result.isEmpty()
          ? Map.of(
              Attributes.builder()
                  .put(stringKey(ATTRIBUTE_TENANT_HASH), UNKNOWN_TENANT_HASH)
                  .build(),
              0L)
          : result;
    }
  }

  /**
   * Native SQL keeps the exact v2-filtered table shape; this read-only scalar count loses no
   * listener side effect.
   */
  private long countMarkingsForTenant(String tenantId) {
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM marking_definitions WHERE tenant_id = ?1")
                .setParameter(1, tenantId)
                .getSingleResult())
        .longValue();
  }

  private static String hashTenantId(String tenantId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(tenantId.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
