package io.openaev.telemetry.metric_collectors;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricRegistry {

  @Lazy private final Meter meter;
  private final List<ObservableDoubleGauge> activeGauges = new ArrayList<>();

  /**
   * Normalizes a free-text metric label value: trimmed, with null/blank falling back to {@code
   * unknown}. This prevents whitespace variants of the same value from becoming separate time
   * series; it does not cap the number of distinct values (callers needing a hard bound must
   * enforce it themselves, see {@code AiMetricCollector#recordAgentProxyCall}).
   */
  public static String normalizeLabel(String value) {
    return value == null || value.isBlank() ? "unknown" : value.trim();
  }

  @PreDestroy
  private void destroy() {
    activeGauges.forEach(ObservableDoubleGauge::close);
  }

  // -- Gauge Registration --

  /** Registers a gauge that reports a single snapshot value polled at export time. */
  public void registerGauge(
      String name, String description, Supplier<Long> valueSupplier, String unit) {
    activeGauges.add(
        meter
            .gaugeBuilder(name)
            .setDescription(description)
            .setUnit(unit)
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(Math.toIntExact(valueSupplier.get()))));
  }

  public void registerGauge(String name, String description, Supplier<Long> valueSupplier) {
    registerGauge(name, description, valueSupplier, "count");
  }

  /** Registers a multi-dimensional gauge. */
  public void registerMultiGauge(
      String name, String description, Supplier<Map<Attributes, Long>> valuesSupplier) {
    registerMultiGauge(name, description, valuesSupplier, "count");
  }

  /** Registers a gauge that reports multiple counts, each with different attributes. */
  public void registerMultiGauge(
      String name,
      String description,
      Supplier<Map<Attributes, Long>> valuesSupplier,
      String unit) {
    activeGauges.add(
        meter
            .gaugeBuilder(name)
            .setDescription(description)
            .setUnit(unit)
            .buildWithCallback(
                observableMeasurement -> {
                  valuesSupplier
                      .get()
                      .forEach(
                          (attributes, value) -> observableMeasurement.record(value, attributes));
                }));
  }
}
