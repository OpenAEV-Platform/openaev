package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.OnDelete;
import io.openaev.annotation.OnDeleteAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
public class Step implements Base {

  @Id
  @Column(name = "step_id")
  @JsonProperty("step_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @Schema(description = "ID for the step")
  private String id;

  @NotNull
  @Column(name = "step_action_class")
  @JsonProperty("step_action_class")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "Action executed by the step")
  private StepActionClass stepAction;

  @Type(JsonType.class)
  @Column(name = "step_output", columnDefinition = "jsonb")
  @JsonProperty("step_output")
  @Schema(description = "Output produced by the step in JSON format")
  private String output;

  @Type(JsonType.class)
  @Column(name = "step_output_parser", columnDefinition = "jsonb")
  @JsonProperty("step_output_parser")
  @Schema(description = "Output parser configuration in JSON format")
  private String outputParser;

  @Type(JsonType.class)
  @Column(name = "step_input", columnDefinition = "jsonb")
  @JsonProperty("step_input")
  @Schema(description = "Inputs provided to the step in JSON format")
  private String input;

  @Type(JsonType.class)
  @Column(name = "step_data", columnDefinition = "jsonb")
  @JsonProperty("step_data")
  @Schema(description = "Configuration step data stored as JSON")
  private String data;

  @Min(0)
  @Column(name = "step_limit_execution")
  @JsonProperty("step_limit_execution")
  @Schema(description = "Maximum number of times this step can be executed")
  private int limitExecution;

  @Column(name = "step_condition_executed")
  @JsonProperty("step_condition_executed")
  @Schema(description = "Condition evaluated to determine whether the step is executed")
  private String conditionExecuted;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "step_status")
  @JsonProperty("step_status")
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "Current status of the step")
  private StepStatus status;

  @Type(JsonType.class)
  @Column(name = "step_condition_key_types", columnDefinition = "jsonb")
  @JsonProperty("step_condition_key_types")
  private List<ConditionKeyType> conditionKeyTypes;

  @Column(name = "step_created_at")
  @JsonProperty("step_created_at")
  @CreationTimestamp
  @Schema(description = "Timestamp when the step was created")
  private Instant createdAt;

  @Column(name = "step_updated_at")
  @JsonProperty("step_updated_at")
  @UpdateTimestamp
  @Schema(description = "Timestamp when the step was last updated")
  private Instant updatedAt;

  // JOIN
  @JoinColumn(name = "step_workflow_id")
  @ManyToOne(fetch = FetchType.LAZY)
  @JsonIgnore
  @Schema(description = "Workflow to which this step belongs")
  private Workflow workflow;

  @JoinColumn(name = "step_template_id")
  @ManyToOne(fetch = FetchType.LAZY)
  @JsonIgnore
  @Schema(description = "Template step from which this step was created")
  private Step stepTemplate;

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "stepTemplate")
  @Builder.Default
  @JsonIgnore
  @Schema(description = "Steps that were executed based on this template step")
  @OnDelete(action = OnDeleteAction.SET_REFERENCE_NULL, fieldName = "stepTemplate")
  private List<Step> stepsExecuted = new ArrayList<>();

  @OneToMany(
      fetch = FetchType.LAZY,
      mappedBy = "step",
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Builder.Default
  @JsonIgnore
  @Schema(description = "Condition links attached to this step")
  private List<ConditionStep> conditionSteps = new ArrayList<>();

  /**
   * Returns all conditions linked to this step via the join table.
   *
   * @return list of conditions linked to this step
   */
  @JsonIgnore
  public List<Condition> getConditions() {
    return conditionSteps.stream().map(ConditionStep::getCondition).toList();
  }
}
