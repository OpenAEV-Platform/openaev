package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScopeMetricCollector {

  private final Meter meter;
  private final MetricRegistry metricRegistry;

  private LongCounter scopeCreatedCounter;
  private LongCounter scopeEntryAddedCounter;

  // Counter
  private final AtomicLong scopeCreatedCount = new AtomicLong(0);
  private final AtomicLong scopeEntryAddedCount = new AtomicLong(0);

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "scope.created",
        "Count scope definitions grouped by mode",
        () -> scopeCreatedCount.getAndSet(0));

    metricRegistry.registerGauge(
        "scope.entry.added",
        "Count newly added scope entries grouped by type and method",
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

  public void trackScopeCreated(ScopeRuleSelectedMode mode, long entryCount) {
    scopeCreatedCounter.add(
        1, Attributes.of(stringKey("mode"), toModeLabel(mode), longKey("entry_count"), entryCount));
  }

  public void trackScopeEntryAdded(ScopeRuleValueType type, ScopeRuleSource source) {
    scopeEntryAddedCounter.add(
        1,
        Attributes.of(
            stringKey("type"), toTypeLabel(type), stringKey("method"), toMethodLabel(source)));
  }

  private String toModeLabel(ScopeRuleSelectedMode mode) {
    if (mode == null) {
      return "unknown";
    }
    String normalized = mode.name().toLowerCase(Locale.ROOT);
    if (normalized.contains("white") || normalized.contains("allow")) {
      return "whitelist";
    }
    if (normalized.contains("black") || normalized.contains("deny")) {
      return "blacklist";
    }
    return normalized;
  }

  private String toTypeLabel(ScopeRuleValueType type) {
    if (type == null) {
      return "hostname";
    }
    return switch (type) {
      case IP -> "ip";
      case IP_SUBNET -> "cidr";
      case ASSET_ID -> "asset";
      case ASSET_GROUP_ID -> "asset_group";
      default -> "hostname";
    };
  }

  private String toMethodLabel(ScopeRuleSource source) {
    if (source == ScopeRuleSource.CSV) {
      return "csv";
    }
    return "manual";
  }
}
