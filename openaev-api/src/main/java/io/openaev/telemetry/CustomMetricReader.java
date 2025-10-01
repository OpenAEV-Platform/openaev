package io.openaev.telemetry;

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.*;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
public class CustomMetricReader implements MetricReader {
  private final OtlpHttpMetricExporter otlpExporter;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final Duration collectInterval;
  private final Duration exportInterval;
  Map<String, MetricData> groupedMetrics = new HashMap<>();

  private final AtomicReference<CollectionRegistration> collectionRef =
      new AtomicReference<>(CollectionRegistration.noop());
  private final AtomicLong collectMetricCount = new AtomicLong(0);

  public CustomMetricReader(
      @NotNull OtlpHttpMetricExporter otlpExporter,
      @NotNull ThreadPoolTaskScheduler taskScheduler,
      @NotNull Duration collectIntervalInput,
      @NotNull Duration exportIntervalInput) {
    this.otlpExporter = otlpExporter;
    this.taskScheduler = taskScheduler;
    this.collectInterval = collectIntervalInput;
    this.exportInterval = exportIntervalInput;
  }

  @Override
  public void register(@NotNull CollectionRegistration collectionRegistration) {
    collectionRef.set(collectionRegistration);
    this.taskScheduler.scheduleAtFixedRate(this::collectAndExportMetrics, this.collectInterval);
  }

  private void collectAndExportMetrics() {
    collectMetrics();
    collectMetricCount.incrementAndGet();
    if (exportInterval.toSeconds() / collectInterval.toSeconds() == collectMetricCount.get()) {
      exportData();
      collectMetricCount.set(0);
    }
  }

  private void collectMetrics() {
    try {
      if (collectionRef.get() != null
          && !Objects.equals(collectionRef.get(), CollectionRegistration.noop())) {
        // Get all metrics
        collectionRef.get().collectAllMetrics().stream()
            .toList()
            .forEach(
                metric -> {
                  String metricName = metric.getName();
                  if (groupedMetrics.containsKey(metricName)) {
                    MetricData existingMetric = groupedMetrics.get(metricName);

                    Collection<DoublePointData> newPoints =
                        new ArrayList<>(existingMetric.getDoubleGaugeData().getPoints());
                    newPoints.addAll(metric.getDoubleGaugeData().getPoints());

                    // Create a new GaugeData object with the updated points
                    GaugeData<DoublePointData> newGaugeData = ImmutableGaugeData.create(newPoints);

                    // Replace the data in existingMetric
                    existingMetric =
                        ImmutableMetricData.createDoubleGauge(
                            existingMetric.getResource(),
                            existingMetric.getInstrumentationScopeInfo(),
                            existingMetric.getName(),
                            existingMetric.getDescription(),
                            existingMetric.getUnit(),
                            newGaugeData);

                    groupedMetrics.put(metricName, existingMetric);
                  } else {
                    groupedMetrics.put(metricName, metric);
                  }
                });
      } else {
        log.error("Telemetry - CollectionRegistration not initialized");
      }
    } catch (Exception e) {
      log.error(String.format("Telemetry - Error during metric collection: %s", e.getMessage()), e);
    }
  }

  private void exportData() {
    if (!groupedMetrics.isEmpty()) {
      log.info("Telemetry - Export the collected data to OTLP exporter");
      otlpExporter.export(new ArrayList<>(groupedMetrics.values()));
      groupedMetrics.clear();
    }
  }

  @Override
  public CompletableResultCode forceFlush() {
    log.info(
        "Telemetry - Flushing and exporting any queued records that have not yet been exported.");
    exportData();
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    log.info("Telemetry - Shutdown the exporter and cleanup any resources");
    taskScheduler.shutdown();
    groupedMetrics.clear();
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public AggregationTemporality getAggregationTemporality(@NotNull InstrumentType instrumentType) {
    return AggregationTemporality.DELTA;
  }
}
