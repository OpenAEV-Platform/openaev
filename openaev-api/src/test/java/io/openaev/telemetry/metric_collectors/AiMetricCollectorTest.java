package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.repository.SettingRepository;
import io.openaev.rest.stream.ai.AiConfig;
import io.openaev.xtmone.XtmOneConfig;
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
@DisplayName("AiMetricCollector Tests")
class AiMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;
  @Mock private AiConfig aiConfig;
  @Mock private XtmOneConfig xtmOneConfig;
  @Mock private SettingRepository settingRepository;

  @InjectMocks private AiMetricCollector collector;

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> multiGaugeCaptor;
  @Captor private ArgumentCaptor<Supplier<Long>> gaugeCaptor;

  @Nested
  @DisplayName("Backend-agnostic feature counting")
  class FeatureCounting {

    @Test
    @DisplayName("legacy endpoint calls and known feature intents land in the same counter")
    void given_legacyCallAndKnownFeatureIntent_should_landInSameCounter() {
      // Legacy /api/ai/* call
      collector.recordAiCall("fix_spelling");
      // Same feature routed through the XTM One agent proxy with its intent
      collector.recordAgentProxyCall("some-agent", "global.fix_spelling");

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("ai_call_count"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot).containsEntry(Attributes.of(stringKey("feature"), "fix_spelling"), 2L);

      // Delta semantics: second collection is empty
      assertThat(multiGaugeCaptor.getValue().get()).isEmpty();
    }

    @Test
    @DisplayName("subject generation telemetry sub-intent lands in generate_subject")
    void given_subjectGenerationSubIntent_should_landInGenerateSubjectFeature() {
      // Legacy /api/ai/generate_subject call
      collector.recordAiCall("generate_subject");
      // Same feature through the XTM One agent proxy: genSubject shares the
      // aev.message_generator catalog intent, disambiguated by the telemetry sub-intent
      collector.recordAgentProxyCall("some-agent", "aev.message_generator.subject");

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("ai_call_count"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsEntry(Attributes.of(stringKey("feature"), "generate_subject"), 2L);
    }

    @Test
    @DisplayName("agent calls without a known feature intent are counted by normalized agent slug")
    void given_unknownOrMissingIntent_should_countAgentCallsBySlug() {
      collector.recordAgentProxyCall("custom-agent", null);
      // Whitespace and case variants collapse into the same normalized label
      collector.recordAgentProxyCall(" Custom-Agent ", "some.unknown_intent");

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("ai_agent_call_count"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsEntry(Attributes.of(stringKey("agent_slug"), "custom-agent"), 2L);
    }

    @Test
    @DisplayName("agent slug labels are length-capped and series growth is bounded")
    void given_abusiveSlugs_should_capLengthAndBoundSeries() {
      // Length cap: a very long slug is truncated to 64 characters
      collector.recordAgentProxyCall("a".repeat(200), null);
      // Series cap: after 100 distinct slugs, further slugs aggregate under "other"
      for (int i = 0; i < 150; i++) {
        collector.recordAgentProxyCall("slug-" + i, null);
      }

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("ai_agent_call_count"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot).containsKey(Attributes.of(stringKey("agent_slug"), "a".repeat(64)));
      assertThat(snapshot).containsKey(Attributes.of(stringKey("agent_slug"), "other"));
      assertThat(snapshot.get(Attributes.of(stringKey("agent_slug"), "other"))).isEqualTo(51L);
      // 100 distinct series + the "other" overflow bucket
      assertThat(snapshot).hasSize(101);
    }
  }

  @Nested
  @DisplayName("Scalar counters")
  class ScalarCounters {

    @Test
    @DisplayName("chatbot messages, TTP extractions and assistant runs reset on collect")
    void given_recordedScalarCounters_should_resetOnCollect() {
      collector.recordChatbotMessage();
      collector.recordChatbotMessage();
      collector.recordTtpExtraction();
      collector.recordInjectAssistantRun();

      collector.init();

      verify(metricRegistry)
          .registerGauge(eq("chatbot_message_count"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(2L);
      assertThat(gaugeCaptor.getValue().get()).isZero();
    }

    @Test
    @DisplayName("detection remediation counts carry the security platform dimension")
    void given_securityPlatforms_should_dimensionDetectionRemediationCounts() {
      collector.recordDetectionRemediation("CrowdStrike Falcon");
      collector.recordDetectionRemediation(null);

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(
              eq("detection_remediation_ai_count"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsEntry(Attributes.of(stringKey("security_platform"), "CrowdStrike Falcon"), 1L)
          .containsEntry(Attributes.of(stringKey("security_platform"), "unknown"), 1L);
    }
  }

  @Nested
  @DisplayName("Configuration gauges")
  class ConfigurationGauges {

    @Test
    @DisplayName("is_ai_enabled carries the trimmed provider type when enabled and none otherwise")
    void given_aiEnabledWithProviderType_should_exposeProviderType() {
      when(aiConfig.isEnabled()).thenReturn(true);
      // Surrounding whitespace in the configuration must not leak into the label
      when(aiConfig.getType()).thenReturn(" mistralai ");

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(
              eq("is_ai_enabled"), any(), multiGaugeCaptor.capture(), eq("boolean"));
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot).containsEntry(Attributes.of(stringKey("type"), "mistralai"), 1L);
    }

    @Test
    @DisplayName("is_ai_enabled falls back to none when the provider type is blank")
    void given_aiEnabledWithBlankProviderType_should_fallBackToNone() {
      when(aiConfig.isEnabled()).thenReturn(true);
      when(aiConfig.getType()).thenReturn("  ");

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(
              eq("is_ai_enabled"), any(), multiGaugeCaptor.capture(), eq("boolean"));
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot).containsEntry(Attributes.of(stringKey("type"), "none"), 1L);
    }
  }
}
