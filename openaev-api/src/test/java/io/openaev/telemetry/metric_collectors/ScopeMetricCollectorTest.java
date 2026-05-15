package io.openaev.telemetry.metric_collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
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
@DisplayName("ScopeMetricCollector")
class ScopeMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;

  @InjectMocks private ScopeMetricCollector scopeMetricCollector;

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> supplierCaptor;

  // ========================================================================
  // scope_created_count
  // ========================================================================
  @Nested
  @DisplayName("Scope created multi-gauge")
  class ScopeCreatedGauge {

    @Test
    @DisplayName("given multiple modes should register multi-gauge and return counts per mode")
    void given_multipleModes_should_registerMultiGaugeAndReturnCountsPerMode() {
      // Arrange
      scopeMetricCollector.recordScopeCreated(ScopeRuleSelectedMode.ALLOWLIST.name(), 2);
      scopeMetricCollector.recordScopeCreated(ScopeRuleSelectedMode.DENYLIST.name(), 1);

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("scope_created_count"),
              eq("Number of entries created in scope rules by mode during the collection interval"),
              supplierCaptor.capture(),
              eq("count"));

      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(2);
      assertThat(snapshot.values()).containsExactlyInAnyOrder(2L, 1L);
    }

    @Test
    @DisplayName("given scope created gauge when collected should reset to zero")
    void given_scopeCreatedGauge_when_collected_should_resetToZero() {
      // Arrange
      scopeMetricCollector.recordScopeCreated(ScopeRuleSelectedMode.ALLOWLIST.name(), 3);

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("scope_created_count"), any(), supplierCaptor.capture(), any());

      Supplier<Map<Attributes, Long>> supplier = supplierCaptor.getValue();
      Map<Attributes, Long> first = supplier.get();
      assertThat(first).hasSize(1);
      assertThat(first.values()).containsExactly(3L);

      // Second call should return empty (reset after getAndSet)
      Map<Attributes, Long> second = supplier.get();
      assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("given same mode called twice should accumulate counts")
    void given_sameModeTwice_should_accumulateCounts() {
      // Arrange
      scopeMetricCollector.recordScopeCreated(ScopeRuleSelectedMode.ALLOWLIST.name(), 2);
      scopeMetricCollector.recordScopeCreated(ScopeRuleSelectedMode.ALLOWLIST.name(), 5);

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("scope_created_count"), any(), supplierCaptor.capture(), any());

      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(1);
      assertThat(snapshot.values()).containsExactly(7L);
    }
  }

  // ========================================================================
  // scope_entry_added_count
  // ========================================================================
  @Nested
  @DisplayName("Scope entry added multi-gauge")
  class ScopeEntryAddedGauge {

    @Test
    @DisplayName(
        "given entries added should register multi-gauge and return counts per type-source")
    void given_entriesAdded_should_registerMultiGaugeAndReturnCountsPerTypeSource() {
      // Arrange
      scopeMetricCollector.recordEntryAdded(
          ScopeRuleValueType.ASSET_ID.name(), ScopeRuleSource.ASSET.name(), 1);
      scopeMetricCollector.recordEntryAdded(
          ScopeRuleValueType.IP.name(), ScopeRuleSource.MANUAL.name(), 3);

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("scope_entry_added_count"),
              eq("Scope entries added by type and source during the collection interval"),
              supplierCaptor.capture());

      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(2);
      assertThat(snapshot.values()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("given entry added gauge when collected should reset to zero")
    void given_entryAddedGauge_when_collected_should_resetToZero() {
      // Arrange
      scopeMetricCollector.recordEntryAdded(
          ScopeRuleValueType.IP.name(), ScopeRuleSource.MANUAL.name(), 1);

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("scope_entry_added_count"), any(), supplierCaptor.capture());

      Supplier<Map<Attributes, Long>> supplier = supplierCaptor.getValue();
      assertThat(supplier.get()).hasSize(1);
      assertThat(supplier.get()).isEmpty();
    }

    @Test
    @DisplayName("given same type-source pair should accumulate counts")
    void given_sameTypeSourcePair_should_accumulateCounts() {
      // Arrange
      scopeMetricCollector.recordEntryAdded(
          ScopeRuleValueType.DOMAIN.name(), ScopeRuleSource.CSV.name(), 4);
      scopeMetricCollector.recordEntryAdded(
          ScopeRuleValueType.DOMAIN.name(), ScopeRuleSource.CSV.name(), 2);

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("scope_entry_added_count"), any(), supplierCaptor.capture());

      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(1);
      assertThat(snapshot.values()).containsExactly(6L);
    }
  }

  // ========================================================================
  // scope_workflow_source_usage_count
  // ========================================================================
  @Nested
  @DisplayName("Source usage multi-gauge")
  class SourceUsageGauge {

    @Test
    @DisplayName("given usage recorded should register multi-gauge with unique workflow count")
    void given_usageRecorded_should_registerMultiGaugeWithUniqueWorkflowCount() {
      // Arrange
      scopeMetricCollector.recordUsage("wf-1", "CSV");
      scopeMetricCollector.recordUsage("wf-2", "CSV");
      scopeMetricCollector.recordUsage("wf-3", "MANUAL");

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("scope_workflow_source_usage_count"),
              eq(
                  "Number of unique workflows using a specific source during the collection interval"),
              supplierCaptor.capture());

      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(2);
      assertThat(snapshot.values()).containsExactlyInAnyOrder(2L, 1L);
    }

    @Test
    @DisplayName("given same workflow recorded twice for same source should count once")
    void given_sameWorkflowTwice_should_countOnce() {
      // Arrange
      scopeMetricCollector.recordUsage("wf-1", "CSV");
      scopeMetricCollector.recordUsage("wf-1", "CSV");

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("scope_workflow_source_usage_count"), any(), supplierCaptor.capture());

      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).hasSize(1);
      assertThat(snapshot.values()).containsExactly(1L);
    }

    @Test
    @DisplayName("given usage gauge when collected should reset to zero")
    void given_usageGauge_when_collected_should_resetToZero() {
      // Arrange
      scopeMetricCollector.recordUsage("wf-1", "CSV");

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("scope_workflow_source_usage_count"), any(), supplierCaptor.capture());

      Supplier<Map<Attributes, Long>> supplier = supplierCaptor.getValue();
      assertThat(supplier.get()).hasSize(1);
      assertThat(supplier.get()).isEmpty();
    }
  }

  // ========================================================================
  // Edge cases
  // ========================================================================
  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("given no records should return empty snapshots")
    void given_noRecords_should_returnEmptySnapshots() {
      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("scope_created_count"), any(), supplierCaptor.capture(), any());

      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("given zero entry count should not appear in snapshot")
    void given_zeroEntryCount_should_notAppearInSnapshot() {
      // Arrange
      scopeMetricCollector.recordScopeCreated(ScopeRuleSelectedMode.ALLOWLIST.name(), 0);

      // Act
      scopeMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("scope_created_count"), any(), supplierCaptor.capture(), any());

      Map<Attributes, Long> snapshot = supplierCaptor.getValue().get();
      assertThat(snapshot).isEmpty();
    }
  }
}
