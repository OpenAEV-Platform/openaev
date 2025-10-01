package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.database.converter.ContentConverter;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "execution_traces")
public class ExecutionTrace implements Base {

  @Id
  @Column(name = "execution_trace_id")
  @JsonProperty("execution_trace_id")
  @GeneratedValue
  @UuidGenerator
  @NotNull
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "execution_inject_status_id")
  @Schema(type = "string")
  @JsonSerialize(using = MonoIdDeserializer.class)
  private InjectStatus injectStatus;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "execution_inject_test_status_id")
  @Schema(type = "string")
  @JsonSerialize(using = MonoIdDeserializer.class)
  private InjectTestStatus injectTestStatus;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "execution_agent_id")
  @Schema(type = "string")
  @JsonSerialize(using = MonoIdDeserializer.class)
  private Agent agent;

  @Column(name = "execution_message")
  @JsonProperty("execution_message")
  @NotNull
  private String message;

  @Column(name = "execution_structured_output")
  @Convert(converter = ContentConverter.class)
  @JsonIgnore
  private ObjectNode structuredOutput;

  @Column(name = "execution_action")
  @JsonProperty("execution_action")
  @Enumerated(EnumType.STRING)
  private ExecutionTraceAction action;

  @Column(name = "execution_status")
  @JsonProperty("execution_status")
  @Enumerated(EnumType.STRING)
  private ExecutionTraceStatus status;

  @Column(name = "execution_time")
  @JsonProperty("execution_time")
  private Instant time;

  @Type(StringArrayType.class)
  @JsonProperty("execution_context_identifiers")
  @Column(name = "execution_context_identifiers")
  private String[] identifiers;

  @CreationTimestamp
  @Column(name = "execution_created_at")
  @JsonProperty("execution_created_at")
  @NotNull
  private Instant creationDate = now();

  @UpdateTimestamp
  @Column(name = "execution_updated_at")
  @JsonProperty("execution_updated_at")
  @NotNull
  private Instant updateDate = now();

  public List<String> getIdentifiers() {
    return identifiers == null ? List.of() : List.of(identifiers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static ExecutionTrace getNewErrorTrace(String message, ExecutionTraceAction action) {
    return new ExecutionTrace(null, ExecutionTraceStatus.ERROR, null, message, action, null, null);
  }

  public static ExecutionTrace getNewErrorTrace(
      String message, ExecutionTraceAction action, Agent agent) {
    return new ExecutionTrace(null, ExecutionTraceStatus.ERROR, null, message, action, agent, null);
  }

  public static ExecutionTrace getNewErrorTrace(
      String message, ExecutionTraceAction action, List<String> identifiers) {
    return new ExecutionTrace(
        null, ExecutionTraceStatus.ERROR, identifiers, message, action, null, null);
  }

  public static ExecutionTrace getNewSuccessTrace(String message, ExecutionTraceAction action) {
    return new ExecutionTrace(
        null, ExecutionTraceStatus.SUCCESS, null, message, action, null, null);
  }

  public static ExecutionTrace getNewSuccessTrace(
      String message, ExecutionTraceAction category, List<String> identifiers) {
    return new ExecutionTrace(
        null, ExecutionTraceStatus.SUCCESS, identifiers, message, category, null, null);
  }

  public static ExecutionTrace getNewInfoTrace(String message, ExecutionTraceAction action) {
    return new ExecutionTrace(null, ExecutionTraceStatus.INFO, null, message, action, null, null);
  }

  public static ExecutionTrace getNewInfoTrace(
      String message, ExecutionTraceAction action, List<String> identifiers) {
    return new ExecutionTrace(
        null, ExecutionTraceStatus.INFO, identifiers, message, action, null, null);
  }

  public static ExecutionTrace getNewInfoTrace(
      String message, ExecutionTraceAction action, Agent agent, List<String> identifiers) {
    return new ExecutionTrace(
        null, ExecutionTraceStatus.INFO, identifiers, message, action, agent, null);
  }

  public static ExecutionTrace from(ExecutionTrace executionTrace, ObjectNode structuredOutput) {
    return new ExecutionTrace(executionTrace, structuredOutput);
  }

  public ExecutionTrace() {}

  public ExecutionTrace(
      InjectStatus injectStatus,
      ExecutionTraceStatus status,
      List<String> identifiers,
      String message,
      ExecutionTraceAction action,
      Agent agent,
      Instant time) {
    this.injectStatus = injectStatus;
    this.status = status;
    this.identifiers = identifiers == null ? new String[0] : identifiers.toArray(new String[0]);
    this.message = message;
    this.time = time == null ? now() : time;
    this.action = action;
    this.agent = agent;
  }

  public ExecutionTrace(ExecutionTrace base, ObjectNode structuredOutput) {
    this.injectStatus = base.injectStatus;
    this.status = base.status;
    this.identifiers = base.identifiers;
    this.message = base.message;
    this.time = base.time;
    this.action = base.action;
    this.agent = base.agent;
    this.structuredOutput = structuredOutput;
  }
}
