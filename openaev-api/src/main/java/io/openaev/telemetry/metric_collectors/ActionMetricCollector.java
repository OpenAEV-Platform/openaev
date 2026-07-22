package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionMetricCollector {
  private final MetricRegistry metricRegistry;

  // Counter
  private final AtomicLong scenarioCreatedCount = new AtomicLong(0);
  private final AtomicLong simulationCreatedCount = new AtomicLong(0);
  private final AtomicLong atomicTestingCreatedCount = new AtomicLong(0);
  private final AtomicLong simulationPlayedCount = new AtomicLong(0);
  private final AtomicLong injectsPlayedByAgentCount = new AtomicLong(0);
  private final AtomicLong injectPlayedWithoutAgentsCount = new AtomicLong(0);
  private final AtomicLong averageWidgetsCount = new AtomicLong(0);
  // Injects played broken down by injector type (additive to the legacy
  // with/without-agents pair, which is kept for continuity).
  private final Map<Attributes, AtomicLong> injectsPlayedByType = new ConcurrentHashMap<>();
  // Executors actually used, broken down by executor type.
  private final Map<Attributes, AtomicLong> executorsUsedByType = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "scenarios_created_count",
        "Number of scenarios created",
        () -> scenarioCreatedCount.getAndSet(0));
    metricRegistry.registerGauge(
        "simulations_created_count",
        "Number of simulations created",
        () -> simulationCreatedCount.getAndSet(0));
    metricRegistry.registerGauge(
        "atomic_testings_created_count",
        "Number of atomic testings created",
        () -> atomicTestingCreatedCount.getAndSet(0));
    metricRegistry.registerGauge(
        "simulations_played_count",
        "Number of simulations played",
        () -> simulationPlayedCount.getAndSet(0));
    metricRegistry.registerGauge(
        "injects_played_requiring_agents_count",
        "Number of injects played with agents required",
        () -> injectsPlayedByAgentCount.getAndSet(0));
    metricRegistry.registerGauge(
        "injects_played_without_agents_count",
        "Number of injects played without requiring agents",
        () -> injectPlayedWithoutAgentsCount.getAndSet(0));
    metricRegistry.registerGauge(
        "average_widgets_created_count",
        "Number of widget Average created",
        () -> averageWidgetsCount.getAndSet(0));
    metricRegistry.registerMultiGauge(
        "injects_played_count",
        "Number of injects played, broken down by injector type",
        this::collectInjectsPlayedByType);
    metricRegistry.registerMultiGauge(
        "executors_used_count",
        "Number of times each executor type was used",
        this::collectExecutorsUsedByType);
  }

  private Map<Attributes, Long> collectInjectsPlayedByType() {
    Map<Attributes, Long> snapshot = new HashMap<>();
    injectsPlayedByType.forEach(
        (attributes, value) -> {
          long collected = value.getAndSet(0);
          if (collected > 0) {
            snapshot.put(attributes, collected);
          }
        });
    return snapshot;
  }

  private Map<Attributes, Long> collectExecutorsUsedByType() {
    Map<Attributes, Long> snapshot = new HashMap<>();
    executorsUsedByType.forEach(
        (attributes, value) -> {
          long collected = value.getAndSet(0);
          if (collected > 0) {
            snapshot.put(attributes, collected);
          }
        });
    return snapshot;
  }

  public void addScenarioCreatedCount() {
    scenarioCreatedCount.incrementAndGet();
    log.info("Increment Scenarios Created Counter");
  }

  public void addSimulationCreatedCount() {
    simulationCreatedCount.incrementAndGet();
    log.info("Increment Simulation Created Counter");
  }

  public void addAtomicTestingCreatedCount() {
    atomicTestingCreatedCount.incrementAndGet();
    log.info("Increment AtomicTestings Created Counter");
  }

  public void addSimulationPlayedCount() {
    simulationPlayedCount.incrementAndGet();
    log.info("Increment Simulation Played Counter");
  }

  public void addSimulationPlayedCount(long count) {
    simulationPlayedCount.addAndGet(count);
    log.info("Increment Simulation Played Counter");
  }

  public void addAverageCreatedCount() {
    averageWidgetsCount.incrementAndGet();
    log.info("Increment Average Created Counter");
  }

  public void removeAverageCreatedCount() {
    averageWidgetsCount.decrementAndGet();
    log.info("Decrement Average Created Counter");
  }

  private void addInjectsPlayedByAgentCount() {
    injectsPlayedByAgentCount.incrementAndGet();
    log.info("Increment Inject Played by agents Counter");
  }

  private void addInjectPlayedWithoutAgentsCount() {
    injectPlayedWithoutAgentsCount.incrementAndGet();
    log.info("Increment Inject Played without agents Counter");
  }

  /** Records that the given executor type was used for an inject execution. */
  public void addExecutorUsedCount(String executorType) {
    try {
      String type = MetricRegistry.normalizeLabel(executorType);
      executorsUsedByType
          .computeIfAbsent(
              Attributes.of(stringKey("executor_type"), type), key -> new AtomicLong(0))
          .incrementAndGet();
    } catch (Exception e) {
      log.error(
          String.format("Error during incrementing executor used count: %s", e.getMessage()), e);
    }
  }

  public void addInjectPlayedCount(String injectorType) {
    try {
      if (OpenAEVImplantContract.TYPE.equals(injectorType)) {
        addInjectsPlayedByAgentCount();
      } else {
        addInjectPlayedWithoutAgentsCount();
      }
      String type = MetricRegistry.normalizeLabel(injectorType);
      injectsPlayedByType
          .computeIfAbsent(
              Attributes.of(stringKey("injector_type"), type), key -> new AtomicLong(0))
          .incrementAndGet();
    } catch (Exception e) {
      log.error(
          String.format("Error during incrementing inject played count: %s", e.getMessage()), e);
    }
  }
}
