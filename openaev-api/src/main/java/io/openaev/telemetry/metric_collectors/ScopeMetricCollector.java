package io.openaev.telemetry.metric_collectors;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScopeMetricCollector {

  private final MetricRegistry metricRegistry;

  // Counter
  private final AtomicLong scopeCreatedCount = new AtomicLong(0);
  private final AtomicLong scopeEntryAddedCount = new AtomicLong(0);

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "scope_created_count",
        "Number of scope definitions created",
        () -> scopeCreatedCount.getAndSet(0));

    metricRegistry.registerGauge(
        "scope_entry_added_count",
        "Number of scope entries added",
        () -> scopeEntryAddedCount.getAndSet(0));
  }

  public void addScopeCreatedCount() {
    scopeCreatedCount.incrementAndGet();
    log.info("Increment Scope Created Counter");
  }

  public void addScopeEntryAddedCount() {
    scopeEntryAddedCount.incrementAndGet();
    log.info("Increment Scope Entry Added Counter");
  }
}
