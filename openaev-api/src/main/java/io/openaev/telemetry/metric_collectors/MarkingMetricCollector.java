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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

  private static final String ATTRIBUTE_RESOURCE_TYPE = "resource_type";
  private static final String ATTRIBUTE_ACTION = "action";
  private static final String RESOURCE_TYPE_ASSET = "asset";
  private static final String RESOURCE_TYPE_GROUP = "group";
  private static final String ACTION_ASSIGN = "assign";
  private static final String ACTION_UNASSIGN = "unassign";

  private final MetricRegistry metricRegistry;
  private final TenantScopedTransaction tenantTx;

  /** Per resource type (asset/group) and action (assign/unassign), see {@link #init()}. */
  private final Map<Attributes, AtomicLong> markingAssignmentStats = new ConcurrentHashMap<>();

  @PersistenceContext private EntityManager entityManager;

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "markings_total",
        "Total number of marking definitions in the current tenant scope",
        this::collectMarkings);
    metricRegistry.registerMultiGauge(
        "marking_assignment_count",
        "Number of markings assigned/unassigned to assets and groups, by resource type and action",
        () -> collectAndReset(markingAssignmentStats));
  }

  /** Records {@code count} markings newly assigned to an asset. */
  public void recordAssetMarkingsAssigned(int count) {
    recordMarkingAssignment(RESOURCE_TYPE_ASSET, ACTION_ASSIGN, count);
  }

  /** Records {@code count} markings removed from an asset. */
  public void recordAssetMarkingsUnassigned(int count) {
    recordMarkingAssignment(RESOURCE_TYPE_ASSET, ACTION_UNASSIGN, count);
  }

  /** Records {@code count} markings newly granted by a group to its members. */
  public void recordGroupMarkingsAssigned(int count) {
    recordMarkingAssignment(RESOURCE_TYPE_GROUP, ACTION_ASSIGN, count);
  }

  /** Records {@code count} markings revoked from a group's grant. */
  public void recordGroupMarkingsUnassigned(int count) {
    recordMarkingAssignment(RESOURCE_TYPE_GROUP, ACTION_UNASSIGN, count);
  }

  private void recordMarkingAssignment(String resourceType, String action, int count) {
    if (count <= 0) {
      return;
    }
    Attributes attributes =
        Attributes.of(
            stringKey(ATTRIBUTE_RESOURCE_TYPE), resourceType, stringKey(ATTRIBUTE_ACTION), action);
    Runnable increment =
        () ->
            markingAssignmentStats
                .computeIfAbsent(attributes, key -> new AtomicLong(0))
                .addAndGet(count);
    // Deferred to afterCommit (same pattern as CommitAwareCacheManager): the caller's
    // transaction can still roll back after this call (e.g. the repository save below it),
    // and a rolled-back assignment must not inflate the exported count.
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      increment.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            increment.run();
          }
        });
  }

  private Map<Attributes, Long> collectAndReset(Map<Attributes, AtomicLong> stats) {
    Map<Attributes, Long> snapshot = new HashMap<>();
    stats.forEach(
        (attributes, value) -> {
          long collected = value.getAndSet(0);
          if (collected > 0) {
            snapshot.put(attributes, collected);
          }
        });
    return snapshot;
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
