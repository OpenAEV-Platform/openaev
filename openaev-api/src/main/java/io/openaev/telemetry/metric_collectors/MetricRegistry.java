package io.openaev.telemetry.metric_collectors;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricRegistry {

  @Lazy private final Meter meter;
  private final List<ObservableDoubleGauge> activeGauges = new ArrayList<>();
  private final List<ObservableLongCounter> activeCounters = new ArrayList<>();

  @PreDestroy
  private void destroy() {
    activeGauges.forEach(ObservableDoubleGauge::close);
    activeCounters.forEach(ObservableLongCounter::close);
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

  // --  Counter Registration --

  /**
   * Registers a dimensional observable counter. Can report multiple attribute combinations via
   * {@code measurement.record(value, attrs)}.
   */
  public void registerObservableCounter(
      String name, String description, Consumer<ObservableLongMeasurement> callback, String unit) {
    activeCounters.add(
        meter
            .counterBuilder(name)
            .setDescription(description)
            .setUnit(unit)
            .buildWithCallback(callback));
  }
}
