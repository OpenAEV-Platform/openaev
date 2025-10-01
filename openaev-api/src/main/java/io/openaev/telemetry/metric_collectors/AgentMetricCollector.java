package io.openaev.telemetry.metric_collectors;

import io.openaev.database.model.Executor;
import io.openaev.executors.ExecutorService;
import io.openaev.service.AgentService;
import io.openaev.telemetry.OpenTelemetryConfig;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Tuple;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMetricCollector {
  private final MetricRegistry metricRegistry;
  private final OpenTelemetryConfig openTelemetryConfig;
  private final AgentService agentService;
  private final ExecutorService executorService;

  private final AtomicReference<Tuple> metrics = new AtomicReference<>(null);
  private volatile Instant lastFetchTime = Instant.MIN;
  private Iterable<Executor> executors = new ArrayList<>();

  @PostConstruct
  public void init() {
    executors = this.executorService.executors();

    metricRegistry.registerGauge(
        "total_agents_deployed",
        "Number of agents deployed",
        () -> getUpdatedMetricValue("total_agents"));

    // Telemetry on agent deployment mode
    metricRegistry.registerGauge(
        "total_service_agents_deployed",
        "Number of agents deployed as service",
        () -> getUpdatedMetricValue("service_agents"));
    metricRegistry.registerGauge(
        "total_session_agents_deployed",
        "Number of agents deployed as session",
        () -> getUpdatedMetricValue("session_agents"));

    // Telemetry on privilege mode
    metricRegistry.registerGauge(
        "total_admin_agents_deployed",
        "Number agents deployed with admin privilege",
        () -> getUpdatedMetricValue("admin_agents"));
    metricRegistry.registerGauge(
        "total_user_agents_deployed",
        "Number agents deployed with user privilege",
        () -> getUpdatedMetricValue("user_agents"));

    // Telemetry on agents executors
    executors.forEach(
        executor ->
            metricRegistry.registerGauge(
                "total_" + executor.getType() + "_deployed",
                "Number of " + executor.getName() + " deployed",
                () -> getUpdatedMetricValue("agent_" + executor.getType())));
  }

  private long getUpdatedMetricValue(String key) {
    if (Duration.between(lastFetchTime, Instant.now())
            .compareTo(openTelemetryConfig.getCollectInterval().minus(Duration.ofMillis(100)))
        >= 0) {
      collectAgentsMetrics();
    }
    return getMetricValue(key);
  }

  private void collectAgentsMetrics() {
    lastFetchTime = Instant.now();
    log.info("Refreshing agent metrics...");
    metrics.set(agentService.getAgentMetrics(executors));
  }

  private long getMetricValue(String key) {
    Tuple currentMetrics = metrics.get();
    return (currentMetrics != null && currentMetrics.get(key) != null)
        ? (Long) currentMetrics.get(key)
        : 0;
  }
}
