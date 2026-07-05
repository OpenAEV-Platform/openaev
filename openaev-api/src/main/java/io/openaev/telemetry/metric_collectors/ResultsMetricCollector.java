package io.openaev.telemetry.metric_collectors;

import static io.openaev.telemetry.metric_collectors.MetricRegistry.normalizeLabel;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Delta counters for the results value loop (expectation validations by collectors), the CTI-driven
 * security coverage flow, workflow executions, payload lifecycle and outbound emails. All counters
 * are delta-based: they report the number of events per collection interval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultsMetricCollector {

  private static final String ATTRIBUTE_COLLECTOR_TYPE = "collector_type";
  private static final String ATTRIBUTE_TYPE = "type";

  private final MetricRegistry metricRegistry;

  private final Map<Attributes, AtomicLong> expectationValidationStats = new ConcurrentHashMap<>();
  private final AtomicLong securityCoveragesProcessedCount = new AtomicLong(0);
  private final AtomicLong coverageScenariosGeneratedCount = new AtomicLong(0);
  private final AtomicLong coverageResultsSentCount = new AtomicLong(0);
  private final AtomicLong workflowRunsCount = new AtomicLong(0);
  private final AtomicLong workflowTimeoutsTriggeredCount = new AtomicLong(0);
  private final Map<Attributes, AtomicLong> payloadCreatedStats = new ConcurrentHashMap<>();
  private final AtomicLong payloadsDuplicatedCount = new AtomicLong(0);
  private final AtomicLong payloadsUpsertedCount = new AtomicLong(0);
  private final AtomicLong emailsSentCount = new AtomicLong(0);

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "expectation_validations_by_collector_count",
        "Expectation validation traces pushed by collectors, by collector type",
        () -> collectAndReset(expectationValidationStats));
    metricRegistry.registerGauge(
        "security_coverages_processed_count",
        "Number of CTI security coverage bundles processed",
        () -> securityCoveragesProcessedCount.getAndSet(0));
    metricRegistry.registerGauge(
        "coverage_scenarios_generated_count",
        "Number of scenarios generated from CTI security coverages",
        () -> coverageScenariosGeneratedCount.getAndSet(0));
    metricRegistry.registerGauge(
        "coverage_results_sent_count",
        "Number of security coverage results sent back to the CTI platform",
        () -> coverageResultsSentCount.getAndSet(0));
    metricRegistry.registerGauge(
        "workflow_runs_count",
        "Number of chaining workflow runs started",
        () -> workflowRunsCount.getAndSet(0));
    metricRegistry.registerGauge(
        "workflow_timeouts_triggered_count",
        "Number of workflow runs force-completed by the timeout safety policy",
        () -> workflowTimeoutsTriggeredCount.getAndSet(0));
    metricRegistry.registerMultiGauge(
        "payloads_created_count",
        "Payloads created, by payload type",
        () -> collectAndReset(payloadCreatedStats));
    metricRegistry.registerGauge(
        "payloads_duplicated_count",
        "Number of payloads duplicated",
        () -> payloadsDuplicatedCount.getAndSet(0));
    metricRegistry.registerGauge(
        "payloads_upserted_count",
        "Number of payloads upserted by collectors",
        () -> payloadsUpsertedCount.getAndSet(0));
    metricRegistry.registerGauge(
        "emails_sent_count",
        "Number of individual emails sent by the platform (one per recipient)",
        () -> emailsSentCount.getAndSet(0));
  }

  /** Records expectation validation traces pushed by a collector. */
  public void recordExpectationValidations(String collectorType, long count) {
    if (count <= 0) {
      return;
    }
    Attributes attributes =
        Attributes.of(stringKey(ATTRIBUTE_COLLECTOR_TYPE), normalizeLabel(collectorType));
    expectationValidationStats
        .computeIfAbsent(attributes, key -> new AtomicLong(0))
        .addAndGet(count);
  }

  public void recordSecurityCoverageProcessed() {
    securityCoveragesProcessedCount.incrementAndGet();
  }

  public void recordCoverageScenarioGenerated() {
    coverageScenariosGeneratedCount.incrementAndGet();
  }

  public void recordCoverageResultsSent(long count) {
    if (count > 0) {
      coverageResultsSentCount.addAndGet(count);
    }
  }

  public void recordWorkflowRun() {
    workflowRunsCount.incrementAndGet();
  }

  public void recordWorkflowTimeoutTriggered() {
    workflowTimeoutsTriggeredCount.incrementAndGet();
  }

  public void recordPayloadCreated(String payloadType) {
    Attributes attributes = Attributes.of(stringKey(ATTRIBUTE_TYPE), normalizeLabel(payloadType));
    payloadCreatedStats.computeIfAbsent(attributes, key -> new AtomicLong(0)).incrementAndGet();
  }

  public void recordPayloadDuplicated() {
    payloadsDuplicatedCount.incrementAndGet();
  }

  public void recordPayloadUpserted() {
    payloadsUpsertedCount.incrementAndGet();
  }

  /** Records outbound emails - one per recipient, since the injector sends individual mails. */
  public void recordEmailsSent(long count) {
    if (count > 0) {
      emailsSentCount.addAndGet(count);
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
