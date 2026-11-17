package io.openaev.telemetry.metric_collectors;

import static io.openaev.telemetry.metric_collectors.MetricRegistry.collectAndReset;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Catalogue content installed and uninstalled, by type and action. */
@Service
@RequiredArgsConstructor
public class ContentMetricCollector {

  private final MetricRegistry metricRegistry;

  private final Map<Attributes, AtomicLong> actions = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "content_actions_count",
        "Catalogue content actions, by content type and action",
        () -> collectAndReset(actions));
  }

  public void addAction(ContentType type, CatalogHistory.Action action) {
    actions
        .computeIfAbsent(
            Attributes.of(
                stringKey("content_type"), type.name(), stringKey("action"), action.name()),
            key -> new AtomicLong(0))
        .incrementAndGet();
  }
}
