package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Execution {

  @Getter
  @JsonProperty("execution_runtime")
  private boolean runtime;

  @Getter
  @Setter
  @JsonProperty("execution_async")
  private boolean async;

  @Getter
  @JsonProperty("execution_start")
  private Instant startTime;

  @JsonProperty("execution_stop")
  private Instant stopTime;

  @Getter
  @Setter
  @JsonProperty("execution_traces")
  private List<ExecutionTrace> traces = new ArrayList<>();

  public Execution() {
    // Default constructor for serialization
  }

  public Execution(boolean runtime) {
    this.runtime = runtime;
    this.startTime = now();
  }

  @SuppressWarnings("unused")
  public void setRuntime(boolean runtime) {
    this.runtime = runtime;
  }

  public void stop() {
    this.stopTime = now();
  }

  public void addTrace(ExecutionTrace context) {
    ExecutionTraceStatus status = context.getStatus();
    if (ExecutionTraceStatus.SUCCESS.equals(status) || ExecutionTraceStatus.INFO.equals(status)) {
      log.info(context.getMessage());
    } else {
      log.error(context.getMessage());
    }
    this.traces.add(context);
  }

  @JsonProperty("execution_time")
  public int getExecutionTime() {
    return (int) (this.stopTime.toEpochMilli() - this.startTime.toEpochMilli());
  }

  public ExecutionStatus getStatus() {
    boolean hasSuccess =
        traces.stream()
            .anyMatch(context -> ExecutionTraceStatus.SUCCESS.equals(context.getStatus()));
    boolean hasError =
        traces.stream().anyMatch(context -> ExecutionTraceStatus.ERROR.equals(context.getStatus()));
    if (!hasSuccess && !hasError) {
      return ExecutionStatus.PENDING;
    } else if (hasSuccess && hasError) {
      return ExecutionStatus.PARTIAL;
    } else {
      return hasSuccess ? ExecutionStatus.SUCCESS : ExecutionStatus.ERROR;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Execution execution = (Execution) o;
    return Objects.equals(stopTime, execution.stopTime)
        && startTime.equals(execution.startTime)
        && Objects.equals(traces, execution.traces);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stopTime, startTime, traces);
  }
}
