package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.opentelemetry.api.common.Attributes;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChainingSafetyPolicyMetricCollector Tests")
class ChainingSafetyPolicyMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;

  @InjectMocks private ChainingSafetyPolicyMetricCollector collector;

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> supplierCaptor;

  @Nested
  @DisplayName("RecordTimeoutConfigured")
  class RecordTimeoutConfigured {

    @Test
    @DisplayName(
        "should register multi-gauge with value_hours, value_minutes, is_default dimensions")
    void shouldIncrementCounter_whenRecordTimeoutConfiguredCalled() {
      // Arrange — record three timeout configurations (1h default, i.e. 1h 0min)
      collector.recordTimeoutConfigured(1L, 0L, true);
      collector.recordTimeoutConfigured(1L, 0L, true);
      collector.recordTimeoutConfigured(1L, 0L, true);

      // Act
      collector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("safety_timeout_configured"), any(), supplierCaptor.capture());
      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(1);

      Attributes expectedAttrs =
          Attributes.of(
              longKey("value_hours"), 1L,
              longKey("value_minutes"), 0L,
              booleanKey("is_default"), true);
      assertThat(snapshot).containsEntry(expectedAttrs, 3L);

      // Second collection should return empty (reset)
      Map<Attributes, Long> second = supplierCaptor.getValue().get();
      assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("should separate default and custom timeout values into distinct dimension sets")
    void shouldSeparateDefaultAndCustomTimeoutValues() {
      // Arrange — 1h 0min (default) vs 2h 0min (custom)
      collector.recordTimeoutConfigured(1L, 0L, true);
      collector.recordTimeoutConfigured(2L, 0L, false);

      // Act
      collector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("safety_timeout_configured"), any(), supplierCaptor.capture());
      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(2);
      assertThat(snapshot.values()).containsExactlyInAnyOrder(1L, 1L);
    }
  }

  @Nested
  @DisplayName("RecordRateLimitConfigured")
  class RecordRateLimitConfigured {

    @Test
    @DisplayName("should register multi-gauge with max_attempts, seconds, is_default dimensions")
    void shouldIncrementCounter_whenRecordRateLimitConfiguredCalled() {
      // Arrange
      collector.recordRateLimitConfigured(5L, 60L, false);
      collector.recordRateLimitConfigured(5L, 60L, false);

      // Act
      collector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("safety_ratelimit_configured"), any(), supplierCaptor.capture());
      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(1);

      Attributes expectedAttrs =
          Attributes.of(
              longKey("max_attempts"), 5L,
              longKey("seconds"), 60L,
              booleanKey("is_default"), false);
      assertThat(snapshot).containsEntry(expectedAttrs, 2L);

      // Second collection should return empty (reset)
      Map<Attributes, Long> second = supplierCaptor.getValue().get();
      assertThat(second).isEmpty();
    }
  }

  @Nested
  @DisplayName("NoEmissionWhenNoChange")
  class NoEmissionWhenNoChange {

    @Test
    @DisplayName("should return empty snapshots when no record method called")
    void shouldNotIncrementAnyCounter_whenNoMethodCalled() {
      // Arrange
      collector.init();

      // Assert — timeout gauge
      verify(metricRegistry)
          .registerMultiGauge(eq("safety_timeout_configured"), any(), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEmpty();

      // Assert — ratelimit gauge
      verify(metricRegistry)
          .registerMultiGauge(eq("safety_ratelimit_configured"), any(), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEmpty();
    }
  }
}
