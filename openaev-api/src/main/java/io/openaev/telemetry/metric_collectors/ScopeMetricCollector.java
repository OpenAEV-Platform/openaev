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

  // Counters reset to 0 after each collect (gauge pattern)
  private final AtomicLong scopeCreatedCount = new AtomicLong(0);
  private final AtomicLong scopeEntryAddedCount = new AtomicLong(0);

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "scope_created_count",
        "Number of scope rules created since last collect",
        () -> scopeCreatedCount.getAndSet(0));

    metricRegistry.registerGauge(
        "scope_entry_added_count",
        "Number of scope entries added since last collect",
        () -> scopeEntryAddedCount.getAndSet(0));
  }

  /**
   * Records that scope rules were created (called once per workflow update that adds new rules).
   *
   * @param entryCount number of new rules created in this batch
   */
  public void recordScopeCreated(String mode, int entryCount) {
    scopeCreatedCount.addAndGet(entryCount);
    log.info("Recorded Scope Created: mode={}, entries={} (total pending: {})",
        mode, entryCount, scopeCreatedCount.get());
  }

  /**
   * Records a single scope entry addition with type and source context.
   */
  public void recordEntryAdded(String type, String source) {
    scopeEntryAddedCount.incrementAndGet();
    log.info("Recorded Entry Added: type={}, source={} (total pending: {})",
        type, source, scopeEntryAddedCount.get());
  }
}
