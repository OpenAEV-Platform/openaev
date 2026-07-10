package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

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
@DisplayName("ResultsMetricCollector Tests")
class ResultsMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;

  @InjectMocks private ResultsMetricCollector collector;

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> multiGaugeCaptor;
  @Captor private ArgumentCaptor<Supplier<Long>> gaugeCaptor;

  @Nested
  @DisplayName("Expectation validations by collector")
  class ExpectationValidations {

    @Test
    @DisplayName("validations are dimensioned by trimmed collector type and reset on collect")
    void given_recordedValidations_should_dimensionByCollectorTypeAndResetOnCollect() {
      collector.recordExpectationValidations("crowdstrike", 3);
      // Whitespace variants collapse into the same trimmed label
      collector.recordExpectationValidations(" crowdstrike ", 2);

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(
              eq("expectation_validations_by_collector_count"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsEntry(Attributes.of(stringKey("collector_type"), "crowdstrike"), 5L);

      // Delta semantics: second collection is empty
      assertThat(multiGaugeCaptor.getValue().get()).isEmpty();
    }

    @Test
    @DisplayName("blank collector types fall back to unknown and non-positive counts are ignored")
    void given_blankTypeAndNonPositiveCounts_should_fallBackToUnknownAndIgnore() {
      collector.recordExpectationValidations(" ", 2);
      collector.recordExpectationValidations(null, 1);
      collector.recordExpectationValidations("crowdstrike", 0);
      collector.recordExpectationValidations("crowdstrike", -5);

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(
              eq("expectation_validations_by_collector_count"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsOnly(Map.entry(Attributes.of(stringKey("collector_type"), "unknown"), 3L));
    }
  }

  @Nested
  @DisplayName("Payload lifecycle counters")
  class PayloadLifecycle {

    @Test
    @DisplayName("payload creations are dimensioned by type with unknown fallback")
    void given_payloadCreations_should_dimensionByTypeWithUnknownFallback() {
      collector.recordPayloadCreated("Command");
      collector.recordPayloadCreated("Command");
      collector.recordPayloadCreated(null);

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("payloads_created_count"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsEntry(Attributes.of(stringKey("type"), "Command"), 2L)
          .containsEntry(Attributes.of(stringKey("type"), "unknown"), 1L);

      // Delta semantics: second collection is empty
      assertThat(multiGaugeCaptor.getValue().get()).isEmpty();
    }

    @Test
    @DisplayName("duplications and upserts reset on collect")
    void given_duplicationsAndUpserts_should_resetOnCollect() {
      collector.recordPayloadDuplicated();
      collector.recordPayloadUpserted();
      collector.recordPayloadUpserted();

      collector.init();

      verify(metricRegistry)
          .registerGauge(eq("payloads_duplicated_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(1L);
      assertThat(gaugeCaptor.getValue().get()).isZero();

      verify(metricRegistry)
          .registerGauge(eq("payloads_upserted_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(2L);
      assertThat(gaugeCaptor.getValue().get()).isZero();
    }
  }

  @Nested
  @DisplayName("Scalar counters")
  class ScalarCounters {

    @Test
    @DisplayName("coverage, workflow and email counters reset on collect")
    void given_recordedScalarCounters_should_resetOnCollect() {
      collector.recordSecurityCoverageProcessed();
      collector.recordCoverageScenarioGenerated();
      collector.recordCoverageResultsSent(4);
      collector.recordWorkflowRun();
      collector.recordWorkflowTimeoutTriggered();
      collector.recordEmailsSent(3);

      collector.init();

      verify(metricRegistry)
          .registerGauge(eq("security_coverages_processed_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(1L);

      verify(metricRegistry)
          .registerGauge(eq("coverage_results_sent_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(4L);

      verify(metricRegistry).registerGauge(eq("workflow_runs_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(1L);

      verify(metricRegistry).registerGauge(eq("emails_sent_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(3L);
      assertThat(gaugeCaptor.getValue().get()).isZero();
    }

    @Test
    @DisplayName("non-positive email and coverage-result counts are ignored")
    void given_nonPositiveCounts_should_notIncrementCounters() {
      collector.recordEmailsSent(0);
      collector.recordEmailsSent(-2);
      collector.recordCoverageResultsSent(0);

      collector.init();

      verify(metricRegistry).registerGauge(eq("emails_sent_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isZero();

      verify(metricRegistry)
          .registerGauge(eq("coverage_results_sent_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isZero();
    }
  }
}
