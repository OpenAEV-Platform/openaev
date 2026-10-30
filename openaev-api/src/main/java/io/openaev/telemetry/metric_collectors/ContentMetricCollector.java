package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Catalogue content: what is installed, updated and revoked, and how fast revocations land. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentMetricCollector {
  private final MetricRegistry metricRegistry;

  private final Map<Attributes, AtomicLong> actions = new ConcurrentHashMap<>();
  private final AtomicLong revokedInjects = new AtomicLong(0);
  private final AtomicLong revocationDelaySeconds = new AtomicLong(0);
  private final AtomicLong revocationsReceived = new AtomicLong(0);

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "content_actions_count",
        "Catalogue content actions, by content type, action and whether the system did it",
        this::collectActions);
    metricRegistry.registerGauge(
        "content_revoked_injects_count",
        "Injects that failed because their payload version is revoked",
        () -> revokedInjects.getAndSet(0));
    metricRegistry.registerGauge(
        "content_revocation_delay_average",
        "Average time between a revocation's publication and its arrival on the platform",
        this::collectAverageDelay,
        "s");
  }

  public void addAction(String type, String action, boolean automatic) {
    try {
      actions
          .computeIfAbsent(
              Attributes.of(
                  stringKey("content_type"),
                  MetricRegistry.normalizeLabel(type),
                  stringKey("action"),
                  MetricRegistry.normalizeLabel(action),
                  stringKey("automatic"),
                  String.valueOf(automatic)),
              key -> new AtomicLong(0))
          .incrementAndGet();
    } catch (Exception e) {
      log.error("Error during incrementing content action count: {}", e.getMessage(), e);
    }
  }

  public void addRevokedInject() {
    revokedInjects.incrementAndGet();
  }

  public void addRevocationDelay(Duration delay) {
    revocationDelaySeconds.addAndGet(Math.max(0, delay.toSeconds()));
    revocationsReceived.incrementAndGet();
  }

  private Map<Attributes, Long> collectActions() {
    Map<Attributes, Long> snapshot = new HashMap<>();
    actions.forEach(
        (attributes, value) -> {
          long collected = value.getAndSet(0);
          if (collected > 0) {
            snapshot.put(attributes, collected);
          }
        });
    return snapshot;
  }

  private long collectAverageDelay() {
    long count = revocationsReceived.getAndSet(0);
    long total = revocationDelaySeconds.getAndSet(0);
    return count == 0 ? 0 : total / count;
  }
}
