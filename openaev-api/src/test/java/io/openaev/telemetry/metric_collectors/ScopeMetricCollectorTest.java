package io.openaev.telemetry.metric_collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

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
@DisplayName("ScopeMetricCollector")
class ScopeMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;

  @InjectMocks private ScopeMetricCollector scopeMetricCollector;

  @Captor private ArgumentCaptor<Supplier<Long>> supplierCaptor;

  @Nested
  @DisplayName("Scope created gauge")
  class ScopeCreatedGauge {

    @Test
    void given_scopeCreated_should_registerGaugeAndReturnCount() {
      // Arrange
      scopeMetricCollector.addScopeCreatedCount();
      scopeMetricCollector.addScopeCreatedCount();
      scopeMetricCollector.addScopeCreatedCount();

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("scope_created_count"),
              eq("Number of scope definitions created"),
              supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(3L);
    }

    @Test
    void given_scopeCreatedGauge_when_collected_should_resetToZero() {
      // Arrange
      scopeMetricCollector.addScopeCreatedCount();

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("scope_created_count"),
              eq("Number of scope definitions created"),
              supplierCaptor.capture());
      Supplier<Long> supplier = supplierCaptor.getValue();
      assertThat(supplier.get()).isEqualTo(1L);
      // Second call should return 0 (reset after getAndSet)
      assertThat(supplier.get()).isZero();
    }
  }

  @Nested
  @DisplayName("Scope entry added gauge")
  class ScopeEntryAddedGauge {

    @Test
    void given_scopeEntryAdded_should_registerGaugeAndReturnCount() {
      // Arrange
      scopeMetricCollector.addScopeEntryAddedCount();
      scopeMetricCollector.addScopeEntryAddedCount();

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("scope_entry_added_count"),
              eq("Number of scope entries added"),
              supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(2L);
    }

    @Test
    void given_scopeEntryAddedGauge_when_collected_should_resetToZero() {
      // Arrange
      scopeMetricCollector.addScopeEntryAddedCount();

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("scope_entry_added_count"),
              eq("Number of scope entries added"),
              supplierCaptor.capture());
      Supplier<Long> supplier = supplierCaptor.getValue();
      assertThat(supplier.get()).isEqualTo(1L);
      // Second call should return 0 (reset after getAndSet)
      assertThat(supplier.get()).isZero();
    }
  }
}
