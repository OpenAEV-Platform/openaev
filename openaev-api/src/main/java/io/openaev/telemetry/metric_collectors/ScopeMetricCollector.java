package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScopeMetricCollector {

  private static final String SCOPE_SELECTED_MODE = "mode";
  private static final String SCOPE_SOURCE = "source";
  private static final String SCOPE_TYPE = "type";

  private final MetricRegistry metricRegistry;

  // 1. KPI: scope.created
  private final Map<Attributes, AtomicLong> scopeCreatedStats = new ConcurrentHashMap<>();

  // 2. KPI: scope.entry.added
  private final Map<Attributes, AtomicLong> entryAddedStats = new ConcurrentHashMap<>();

  // 3. Product: Usage of CSV or Manual Inputs in workflows
  private final Map<Attributes, Set<String>> sourceUsageStats = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    // Register: scope.created
    metricRegistry.registerMultiGauge(
        "scope_created_count",
        "Number of entries created in scope rules by mode",
        () -> collectAndResetVolume(scopeCreatedStats),
        "count");

    // Register: scope.entry.added
    metricRegistry.registerMultiGauge(
        "scope_entry_added_count",
        "Total scope entries added by type and source",
        () -> collectAndResetVolume(entryAddedStats));

    // Register: usage
    metricRegistry.registerMultiGauge(
        "scope_workflow_source_usage_total",
        "Number of unique workflows using a specific source",
        () -> collectAndResetUsage(sourceUsageStats));
  }

  /** KP1. Record Creation Patterns (Allowlist/Denylist) */
  public void recordScopeCreated(String mode, int entryCount) {
    Attributes attrs = Attributes.of(stringKey(SCOPE_SELECTED_MODE), mode.toLowerCase());
    scopeCreatedStats.computeIfAbsent(attrs, k -> new AtomicLong(0)).addAndGet(entryCount);
  }

  /** KPI. Record Type and Source Patterns (e.g. DOMAIN|CSV, IP|Manual) */
  public void recordEntryAdded(String type, String source, int count) {
    Attributes attrs =
        Attributes.of(
            stringKey(SCOPE_TYPE), type.toLowerCase(),
            stringKey(SCOPE_SOURCE), source.toLowerCase());
    entryAddedStats.computeIfAbsent(attrs, k -> new AtomicLong(0)).addAndGet(count);
  }

  /** KPI. Record Source Usage (CSV vs Manual) */
  public void recordUsage(String workflowId, String source) {
    Attributes attrs = Attributes.of(stringKey(SCOPE_SOURCE), source.toLowerCase());
    sourceUsageStats
        .computeIfAbsent(attrs, k -> Collections.synchronizedSet(new HashSet<>()))
        .add(workflowId);
  }

  private Map<Attributes, Long> collectAndResetVolume(Map<Attributes, AtomicLong> stats) {
    Map<Attributes, Long> snap = new HashMap<>();
    log.info("Refreshing Scope volume metrics...");
    stats.forEach(
        (k, v) -> {
          long val = v.getAndSet(0);
          if (val > 0) {
            snap.put(k, val);
          }
        });
    return snap;
  }

  private Map<Attributes, Long> collectAndResetUsage(Map<Attributes, Set<String>> stats) {
    Map<Attributes, Long> snap = new HashMap<>();
    log.info("Refreshing Scope usage metrics...");
    stats.forEach(
        (attrs, set) -> {
          synchronized (set) {
            if (!set.isEmpty()) {
              snap.put(attrs, (long) set.size());
              set.clear(); // Reset for next interval
            }
          }
        });
    return snap;
  }
}
