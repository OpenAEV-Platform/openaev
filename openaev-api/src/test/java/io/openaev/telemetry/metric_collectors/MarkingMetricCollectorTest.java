package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.opentelemetry.api.common.Attributes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarkingMetricCollector Tests")
class MarkingMetricCollectorTest {

  private static final String MARKINGS_TOTAL = "markings_total";
  private static final String ATTRIBUTE_TENANT_HASH = "tenant_hash";
  private static final String UNKNOWN_TENANT_HASH = "unknown";

  @Mock private MetricRegistry metricRegistry;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private EntityManager entityManager;
  @Mock private Query nativeQuery;

  @Nested
  @DisplayName("Multi-gauge registration")
  class MultiGaugeRegistration {

    @Test
    @DisplayName("without active tenants the gauge emits a safe zero sample")
    void given_noActiveTenants_should_emitSafeUnknownSample() {
      // Arrange
      MarkingMetricCollector collector = createCollector();
      AtomicReference<java.util.function.Supplier<Map<Attributes, Long>>> registeredGauge =
          new AtomicReference<>();
      doAnswer(
              invocation -> {
                registeredGauge.set(invocation.getArgument(2));
                return null;
              })
          .when(metricRegistry)
          .registerMultiGauge(eq(MARKINGS_TOTAL), anyString(), any());
      doAnswer(invocation -> null).when(tenantTx).forEachTenant(any());

      // Act
      collector.init();
      verify(metricRegistry).registerMultiGauge(eq(MARKINGS_TOTAL), anyString(), any());
      Map<Attributes, Long> snapshot = registeredGauge.get().get();

      // Assert
      assertThat(snapshot)
          .containsExactly(
              Map.entry(
                  Attributes.builder()
                      .put(stringKey(ATTRIBUTE_TENANT_HASH), UNKNOWN_TENANT_HASH)
                      .build(),
                  0L));
      verify(tenantTx).forEachTenant(any());
      verifyNoMoreInteractions(tenantTx);
      verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("with multiple active tenants the gauge emits one sample per tenant hash")
    void given_multipleActiveTenants_should_emitPerTenantSamples() {
      // Arrange
      MarkingMetricCollector collector = createCollector();
      AtomicReference<java.util.function.Supplier<Map<Attributes, Long>>> registeredGauge =
          new AtomicReference<>();
      String tenantId1 = "00000000-0000-0000-0000-000000000123";
      String tenantId2 = "00000000-0000-0000-0000-000000000456";
      String expectedHash1 = hashTenantId(tenantId1);
      String expectedHash2 = hashTenantId(tenantId2);
      doAnswer(
              invocation -> {
                registeredGauge.set(invocation.getArgument(2));
                return null;
              })
          .when(metricRegistry)
          .registerMultiGauge(eq(MARKINGS_TOTAL), anyString(), any());
      doAnswer(
              invocation -> {
                java.util.function.Consumer<String> work = invocation.getArgument(0);
                work.accept(tenantId1);
                work.accept(tenantId2);
                return null;
              })
          .when(tenantTx)
          .forEachTenant(any());
      when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
      when(nativeQuery.setParameter(anyInt(), any())).thenReturn(nativeQuery);
      when(nativeQuery.getSingleResult()).thenReturn(3L, 5L);

      // Act
      collector.init();
      verify(metricRegistry).registerMultiGauge(eq(MARKINGS_TOTAL), anyString(), any());
      Map<Attributes, Long> snapshot = registeredGauge.get().get();

      // Assert
      assertThat(snapshot)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  Attributes.builder().put(stringKey(ATTRIBUTE_TENANT_HASH), expectedHash1).build(),
                  3L,
                  Attributes.builder().put(stringKey(ATTRIBUTE_TENANT_HASH), expectedHash2).build(),
                  5L));
      verify(tenantTx).forEachTenant(any());
      verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(anyString());
      verify(nativeQuery).setParameter(1, tenantId1);
      verify(nativeQuery).setParameter(1, tenantId2);
      verify(nativeQuery, org.mockito.Mockito.times(2)).getSingleResult();
      verifyNoMoreInteractions(tenantTx);
    }
  }

  @Nested
  @DisplayName("Marking assignment gauge")
  class MarkingAssignmentGauge {

    private static final String MARKING_ASSIGNMENT_COUNT = "marking_assignment_count";

    @AfterEach
    void tearDown() {
      // Never leave synchronization active for the next test on this thread.
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.clearSynchronization();
      }
    }

    @Test
    @DisplayName("given_noActiveTransaction_should_exportImmediatelyThenResetOnNextCollect")
    void given_noActiveTransaction_should_exportImmediatelyThenResetOnNextCollect() {
      // Arrange
      MarkingMetricCollector collector = createCollector();
      Supplier<Map<Attributes, Long>> gauge = registerAndCaptureAssignmentGauge(collector);

      // Act
      collector.recordAssetMarkingsAssigned(2);

      // Assert — exported on the first poll, then the counter resets so a second poll with no
      // new activity reports nothing.
      assertThat(gauge.get()).containsExactly(Map.entry(attributesOf("asset", "assign"), 2L));
      assertThat(gauge.get()).isEmpty();
    }

    @Test
    @DisplayName("given_assetAndGroupActivity_should_emitOneSeriesPerResourceTypeAndAction")
    void given_assetAndGroupActivity_should_emitOneSeriesPerResourceTypeAndAction() {
      // Arrange
      MarkingMetricCollector collector = createCollector();
      Supplier<Map<Attributes, Long>> gauge = registerAndCaptureAssignmentGauge(collector);

      // Act
      collector.recordAssetMarkingsAssigned(2);
      collector.recordAssetMarkingsUnassigned(1);
      collector.recordGroupMarkingsAssigned(3);
      collector.recordGroupMarkingsUnassigned(4);

      // Assert
      assertThat(gauge.get())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  attributesOf("asset", "assign"), 2L,
                  attributesOf("asset", "unassign"), 1L,
                  attributesOf("group", "assign"), 3L,
                  attributesOf("group", "unassign"), 4L));
    }

    @Test
    @DisplayName("given_zeroOrNegativeCount_should_recordNothing")
    void given_zeroOrNegativeCount_should_recordNothing() {
      // Arrange
      MarkingMetricCollector collector = createCollector();
      Supplier<Map<Attributes, Long>> gauge = registerAndCaptureAssignmentGauge(collector);

      // Act
      collector.recordAssetMarkingsAssigned(0);
      collector.recordGroupMarkingsUnassigned(-5);

      // Assert
      assertThat(gauge.get()).isEmpty();
    }

    @Test
    @DisplayName("given_activeTransaction_should_deferExportUntilAfterCommit")
    void given_activeTransaction_should_deferExportUntilAfterCommit() {
      // Arrange
      MarkingMetricCollector collector = createCollector();
      Supplier<Map<Attributes, Long>> gauge = registerAndCaptureAssignmentGauge(collector);
      TransactionSynchronizationManager.initSynchronization();

      // Act — recorded mid-transaction, before the caller's repository save/commit.
      collector.recordGroupMarkingsAssigned(5);

      // Assert — not visible yet: a poll before commit must not export an assignment that could
      // still roll back.
      assertThat(gauge.get()).isEmpty();

      // Act — simulate the transaction committing.
      TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

      // Assert — now exported.
      assertThat(gauge.get()).containsExactly(Map.entry(attributesOf("group", "assign"), 5L));
    }

    @Test
    @DisplayName("given_transactionRollsBack_should_neverExportTheAssignment")
    void given_transactionRollsBack_should_neverExportTheAssignment() {
      // Arrange
      MarkingMetricCollector collector = createCollector();
      Supplier<Map<Attributes, Long>> gauge = registerAndCaptureAssignmentGauge(collector);
      TransactionSynchronizationManager.initSynchronization();

      // Act — Spring never calls afterCommit() on a rolled-back synchronization, only
      // afterCompletion(STATUS_ROLLED_BACK).
      collector.recordAssetMarkingsAssigned(7);
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      // Assert
      assertThat(gauge.get()).isEmpty();
    }

    private Supplier<Map<Attributes, Long>> registerAndCaptureAssignmentGauge(
        MarkingMetricCollector collector) {
      AtomicReference<Supplier<Map<Attributes, Long>>> registeredGauge = new AtomicReference<>();
      // Matched generically on name: init() also registers the unrelated "markings_total" gauge,
      // and under strict stubbing an eq(MARKING_ASSIGNMENT_COUNT) matcher would make that other
      // call a PotentialStubbingProblem instead of a no-op.
      doAnswer(
              invocation -> {
                String name = invocation.getArgument(0);
                if (MARKING_ASSIGNMENT_COUNT.equals(name)) {
                  registeredGauge.set(invocation.getArgument(2));
                }
                return null;
              })
          .when(metricRegistry)
          .registerMultiGauge(anyString(), anyString(), any());

      collector.init();

      return registeredGauge.get();
    }

    private Attributes attributesOf(String resourceType, String action) {
      return Attributes.of(stringKey("resource_type"), resourceType, stringKey("action"), action);
    }
  }

  private MarkingMetricCollector createCollector() {
    MarkingMetricCollector collector = new MarkingMetricCollector(metricRegistry, tenantTx);
    try {
      Field entityManagerField = MarkingMetricCollector.class.getDeclaredField("entityManager");
      entityManagerField.setAccessible(true);
      entityManagerField.set(collector, entityManager);
      return collector;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to inject mocked EntityManager", e);
    }
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
