package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Setter
@Getter
@Entity
@Table(name = "injects_statuses")
public class InjectStatus extends BaseInjectStatus {

  @Type(JsonType.class)
  @Column(name = "status_payload_output", columnDefinition = "json")
  @JsonProperty("status_payload_output")
  private StatusPayload payloadOutput;

  @OneToMany(
      mappedBy = "injectStatus",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @JsonProperty("status_traces")
  private List<ExecutionTrace> traces = new ArrayList<>();

  // region transient
  public List<String> statusIdentifiers() {
    return this.getTraces().stream().flatMap(ex -> ex.getIdentifiers().stream()).toList();
  }

  @JsonIgnore
  public Map<String, Agent> getStatusMapIdentifierAgent() {
    Map<String, Agent> info = new HashMap<>();
    this.getTraces()
        .forEach(
            t -> {
              if (t.getAgent() != null
                  && t.getIdentifiers() != null
                  && !t.getIdentifiers().isEmpty()) {
                info.put(t.getIdentifiers().getFirst(), t.getAgent());
              }
            });
    return info;
  }

  public void addTrace(ExecutionTrace trace) {
    this.getTraces().add(trace);
  }

  // Used for global traces of inject
  public void addTrace(
      ExecutionTraceStatus status, String message, ExecutionTraceAction action, Agent agent) {
    ExecutionTrace newTrace =
        new ExecutionTrace(this, status, List.of(), message, action, agent, null);
    this.getTraces().add(newTrace);
  }

  public void addMayBePreventedTrace(String message, ExecutionTraceAction action, Agent agent) {
    ExecutionTrace newTrace =
        new ExecutionTrace(
            this, ExecutionTraceStatus.MAYBE_PREVENTED, List.of(), message, action, agent, null);
    this.getTraces().add(newTrace);
  }

  public void addErrorTrace(String message, ExecutionTraceAction action) {
    ExecutionTrace newTrace =
        new ExecutionTrace(
            this, ExecutionTraceStatus.ERROR, List.of(), message, action, null, null);
    this.getTraces().add(newTrace);
  }

  public void addInfoTrace(String message, ExecutionTraceAction action) {
    ExecutionTrace newTrace =
        new ExecutionTrace(this, ExecutionTraceStatus.INFO, List.of(), message, action, null, null);
    this.getTraces().add(newTrace);
  }

  public void addWarningTrace(String message, ExecutionTraceAction action) {
    ExecutionTrace newTrace =
        new ExecutionTrace(
            this, ExecutionTraceStatus.WARNING, List.of(), message, action, null, null);
    this.getTraces().add(newTrace);
  }
}
