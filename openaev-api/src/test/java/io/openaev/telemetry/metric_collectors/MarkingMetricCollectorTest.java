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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
