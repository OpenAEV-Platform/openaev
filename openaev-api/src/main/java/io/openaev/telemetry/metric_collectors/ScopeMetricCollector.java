package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScopeMetricCollector {

  private final MetricRegistry metricRegistry;
  private LongCounter scopeCreatedCounter;
  private LongCounter scopeEntryAddedCounter;

  @PostConstruct
  public void init() {
    this.scopeCreatedCounter =
        metricRegistry.registerCounter(
            "scope.created", "Number of scope definitions created", "count");

    this.scopeEntryAddedCounter =
        metricRegistry.registerCounter(
            "scope.entry.added", "Number of scope entries added", "count");
  }

  public void recordScopeCreated(String mode, int entryCount) {
    Attributes attrs =
        Attributes.of(
            stringKey("mode"), mode.toLowerCase(), longKey("entry_count"), (long) entryCount);
    scopeCreatedCounter.add(1, attrs);
    log.info("Recorded Scope Created: mode={}, entries={}", mode, entryCount);
  }

  public void recordEntryAdded(String type, String method) {
    Attributes attrs =
        Attributes.of(
            stringKey("type"), type.toLowerCase(),
            stringKey("method"), method.toLowerCase());
    scopeEntryAddedCounter.add(1, attrs);
    log.info("Recorded Entry Added: type={}, method={}", type, method);
  }
}
