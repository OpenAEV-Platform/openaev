package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScopeMetricCollector {

  private final MetricRegistry metricRegistry;

  // KPI 1: scope.created {mode="allowlist"|"denylist"}
  private final Map<Attributes, AtomicLong> scopeCreatedCounters = new ConcurrentHashMap<>();

  // KPI 2: scope.entry.added {type="ip", source="manual"}
  private final Map<Attributes, AtomicLong> entryAddedCounters = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    metricRegistry.registerObservableCounter(
        "scope.created",
        "Number of scope rules created, by mode",
        measurement ->
            scopeCreatedCounters.forEach((attrs, count) -> measurement.record(count.get(), attrs)),
        "count");

    metricRegistry.registerObservableCounter(
        "scope.entry.added",
        "Number of scope entries added, by type and source",
        measurement ->
            entryAddedCounters.forEach((attrs, count) -> measurement.record(count.get(), attrs)),
        "count");
  }

  /** Records scope rules created for a given mode (KPI 1). */
  public void recordScopeCreated(String mode, int entryCount) {
    Attributes attrs = Attributes.of(stringKey("mode"), mode.toLowerCase());
    scopeCreatedCounters.computeIfAbsent(attrs, k -> new AtomicLong()).addAndGet(entryCount);
    log.info("Recorded Scope Created: mode={}, entries={}", mode, entryCount);
  }

  /** Records a single scope entry addition (KPI 2). */
  public void recordEntryAdded(String type, String source) {
    Attributes attrs =
        Attributes.of(
            stringKey("type"), type.toLowerCase(), stringKey("source"), source.toLowerCase());
    entryAddedCounters.computeIfAbsent(attrs, k -> new AtomicLong()).incrementAndGet();
    log.info("Recorded Entry Added: type={}, source={}", type, source);
  }
}
