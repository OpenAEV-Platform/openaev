package io.openaev.telemetry.metric_collectors;

import static io.openaev.telemetry.metric_collectors.MetricRegistry.normalizeLabel;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.database.model.SettingKeys;
import io.openaev.database.repository.SettingRepository;
import io.openaev.rest.stream.ai.AiConfig;
import io.openaev.xtmone.XtmOneConfig;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Telemetry for the AI capabilities. Backend-agnostic by design: an AI text generation or a TTP
 * extraction is the SAME feature whether it is served by the legacy path (direct LLM `/api/ai/*`,
 * legacy webservices) or by an XTM One agent, so no counter carries a legacy/xtm_one dimension.
 * Counters are recorded at the feature entry point, before any routing branch. The before/after XTM
 * One adoption analysis is done in analytics by segmenting instances on the `is_xtm_one_configured`
 * gauge exported here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMetricCollector {

  private static final String ATTRIBUTE_FEATURE = "feature";
  private static final String ATTRIBUTE_AGENT_SLUG = "agent_slug";
  private static final String ATTRIBUTE_SECURITY_PLATFORM = "security_platform";
  private static final String ATTRIBUTE_TYPE = "type";

  /** Guardrails for the user-supplied agent slug label (see #recordAgentProxyCall). */
  private static final int MAX_AGENT_SLUG_LENGTH = 64;

  private static final int MAX_AGENT_SLUG_SERIES = 100;
  private static final String OVERFLOW_AGENT_SLUG = "other";

  /**
   * Mapping from XTM One feature intents to the canonical feature labels used by the legacy
   * endpoints - so a feature call lands in the SAME `ai_call_count` datapoint whichever backend
   * serves it. Agent proxy calls that do not carry a known feature intent are counted separately in
   * `ai_agent_call_count` (never both).
   */
  private static final Map<String, String> INTENT_TO_FEATURE =
      Map.of(
          "global.fix_spelling", "fix_spelling",
          "global.make_it_shorter", "make_shorter",
          "global.make_it_longer", "make_longer",
          "global.change_tone", "change_tone",
          "global.summarize", "summarize",
          "global.explain", "explain",
          "aev.message_generator", "generate_message",
          // Telemetry-only sub-intent: subject generation shares the aev.message_generator
          // catalog intent, the frontend disambiguates it for feature counting.
          "aev.message_generator.subject", "generate_subject",
          "aev.media_article_generator", "generate_media");

  private final MetricRegistry metricRegistry;
  private final AiConfig aiConfig;
  private final XtmOneConfig xtmOneConfig;
  private final SettingRepository settingRepository;

  private final Map<Attributes, AtomicLong> aiCallStats = new ConcurrentHashMap<>();
  private final Map<Attributes, AtomicLong> agentCallStats = new ConcurrentHashMap<>();
  private final AtomicLong chatbotMessageCount = new AtomicLong(0);
  private final AtomicLong ttpExtractionCount = new AtomicLong(0);
  private final Map<Attributes, AtomicLong> detectionRemediationStats = new ConcurrentHashMap<>();
  private final AtomicLong injectAssistantRunCount = new AtomicLong(0);

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "ai_call_count",
        "AI feature calls broken down by feature, whichever backend serves them",
        () -> collectAndReset(aiCallStats));
    metricRegistry.registerGauge(
        "chatbot_message_count",
        "Number of chatbot (Ask Ariane) messages sent",
        () -> chatbotMessageCount.getAndSet(0));
    metricRegistry.registerMultiGauge(
        "ai_agent_call_count",
        "XTM One agent proxy calls not mapped to a known feature intent, by agent slug",
        () -> collectAndReset(agentCallStats));
    metricRegistry.registerGauge(
        "ttp_extraction_count",
        "Number of AI TTP extractions (legacy webservice and XTM One combined)",
        () -> ttpExtractionCount.getAndSet(0));
    metricRegistry.registerMultiGauge(
        "detection_remediation_ai_count",
        "AI detection/remediation rule generations broken down by security platform",
        () -> collectAndReset(detectionRemediationStats));
    metricRegistry.registerGauge(
        "inject_assistant_run_count",
        "Number of scenario inject assistant runs",
        () -> injectAssistantRunCount.getAndSet(0));
    metricRegistry.registerMultiGauge(
        "is_ai_enabled",
        "Built-in LLM configuration state with the provider type as dimension",
        this::collectAiEnabled,
        "boolean");
    metricRegistry.registerGauge(
        "is_xtm_one_configured",
        "XTM One is configured (url and token) - segmentation key for adoption analysis",
        () -> xtmOneConfig.isConfigured() ? 1L : 0L,
        "boolean");
    metricRegistry.registerGauge(
        "is_chatbot_cgu_accepted",
        "Filigran chatbot AI CGU accepted",
        this::isChatbotCguAccepted,
        "boolean");
  }

  /** Records one AI feature call (legacy endpoint or XTM One agent with a feature intent). */
  public void recordAiCall(String feature) {
    if (feature == null || feature.isBlank()) {
      return;
    }
    Attributes attributes = Attributes.of(stringKey(ATTRIBUTE_FEATURE), feature);
    aiCallStats.computeIfAbsent(attributes, key -> new AtomicLong(0)).incrementAndGet();
  }

  /** Records one chatbot (Ask Ariane) message. */
  public void recordChatbotMessage() {
    chatbotMessageCount.incrementAndGet();
  }

  /**
   * Records one XTM One agent proxy call. When the call carries a known feature intent it lands in
   * the backend-agnostic per-feature counter; otherwise it is counted as a generic agent call.
   *
   * <p>The agent slug is user-supplied, so it is normalized (trimmed, lowercased, length-capped)
   * and the number of distinct slug series is bounded: once {@link #MAX_AGENT_SLUG_SERIES} series
   * exist, further slugs are aggregated under {@code other}. This keeps both the metric cardinality
   * and the in-memory stats map bounded even if upstream slug validation is bypassed.
   */
  public void recordAgentProxyCall(String agentSlug, String intent) {
    String feature = intent == null ? null : INTENT_TO_FEATURE.get(intent.trim());
    if (feature != null) {
      recordAiCall(feature);
      return;
    }
    String slug = normalizeLabel(agentSlug).toLowerCase(Locale.ROOT);
    if (slug.length() > MAX_AGENT_SLUG_LENGTH) {
      slug = slug.substring(0, MAX_AGENT_SLUG_LENGTH);
    }
    Attributes attributes = Attributes.of(stringKey(ATTRIBUTE_AGENT_SLUG), slug);
    AtomicLong counter = agentCallStats.get(attributes);
    if (counter == null) {
      // Serialize new-series creation so the cap check and the insert are atomic;
      // the hot path (existing series) above stays lock-free.
      synchronized (agentCallStats) {
        if (!agentCallStats.containsKey(attributes)
            && agentCallStats.size() >= MAX_AGENT_SLUG_SERIES) {
          attributes = Attributes.of(stringKey(ATTRIBUTE_AGENT_SLUG), OVERFLOW_AGENT_SLUG);
        }
        counter = agentCallStats.computeIfAbsent(attributes, key -> new AtomicLong(0));
      }
    }
    counter.incrementAndGet();
  }

  /** Records one TTP extraction attempt, before the legacy/XTM One routing branch. */
  public void recordTtpExtraction() {
    ttpExtractionCount.incrementAndGet();
  }

  /** Records one detection/remediation rule generation attempt, before the routing branch. */
  public void recordDetectionRemediation(String securityPlatformName) {
    String platform = normalizeLabel(securityPlatformName);
    Attributes attributes = Attributes.of(stringKey(ATTRIBUTE_SECURITY_PLATFORM), platform);
    detectionRemediationStats
        .computeIfAbsent(attributes, key -> new AtomicLong(0))
        .incrementAndGet();
  }

  /** Records one scenario inject assistant run. */
  public void recordInjectAssistantRun() {
    injectAssistantRunCount.incrementAndGet();
  }

  private Map<Attributes, Long> collectAiEnabled() {
    boolean enabled = aiConfig.isEnabled();
    String configuredType = aiConfig.getType();
    String type =
        enabled && configuredType != null && !configuredType.isBlank()
            ? configuredType.trim()
            : "none";
    return Map.of(Attributes.of(stringKey(ATTRIBUTE_TYPE), type), enabled ? 1L : 0L);
  }

  private long isChatbotCguAccepted() {
    try {
      return settingRepository
              .findByKeyAndTenantIsNull(SettingKeys.FILIGRAN_CHATBOT_AI_CGU_STATUS.key())
              .map(setting -> "enabled".equalsIgnoreCase(setting.getValue()))
              .orElse(false)
          ? 1L
          : 0L;
    } catch (Exception e) {
      log.error("Telemetry - Failed to read chatbot CGU status", e);
      return 0L;
    }
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
}
