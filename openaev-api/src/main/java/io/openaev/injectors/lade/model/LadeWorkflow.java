package io.openaev.injectors.lade.model;

import io.openaev.database.model.ExecutionTrace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
public class LadeWorkflow {

  private final List<ExecutionTrace> traces = new ArrayList<>();

  @Setter private boolean done = false;

  @Setter private boolean fail = false;

  @Setter private Instant stopTime;

  public void addTrace(ExecutionTrace trace) {
    this.traces.add(trace);
  }
}
