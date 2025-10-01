package io.openaev.telemetry.metric_collectors;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricRegistry {
  @Lazy private final Meter meter;
  private final List<ObservableDoubleGauge> activeGauges = new ArrayList<>();

  @PreDestroy
  private void destroy() {
    if (!activeGauges.isEmpty()) {
      activeGauges.forEach(ObservableDoubleGauge::close);
    }
  }

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
}
