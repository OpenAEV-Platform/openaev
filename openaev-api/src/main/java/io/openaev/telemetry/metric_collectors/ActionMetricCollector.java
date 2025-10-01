package io.openaev.telemetry.metric_collectors;

import io.openaev.injectors.caldera.CalderaContract;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import jakarta.annotation.PostConstruct;
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

  private void addInjectsPlayedByAgentCount() {
    injectsPlayedByAgentCount.incrementAndGet();
    log.info("Increment Inject Played by agents Counter");
  }

  private void addInjectPlayedWithoutAgentsCount() {
    injectPlayedWithoutAgentsCount.incrementAndGet();
    log.info("Increment Inject Played without agents Counter");
  }

  public void addInjectPlayedCount(String injectorType) {
    try {
      if (CalderaContract.TYPE.equals(injectorType)
          || OpenAEVImplantContract.TYPE.equals(injectorType)) {
        addInjectsPlayedByAgentCount();
      } else {
        addInjectPlayedWithoutAgentsCount();
      }
    } catch (Exception e) {
      log.error(
          String.format("Error during incrementing inject played count: %s", e.getMessage()), e);
    }
  }
}
