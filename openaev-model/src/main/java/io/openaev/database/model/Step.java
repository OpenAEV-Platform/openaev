package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(ModelBaseListener.class)
public class Step {

  @Id
  @Column(name = "step_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Schema(description = "ID of the step")
  private String id;

  @Column(name = "step_action_class")
  @Enumerated(EnumType.STRING)
  private STEP_ACTION_CLASS stepAction;

  @Type(JsonType.class)
  @JsonProperty("step_output")
  @Column(name = "output", columnDefinition = "jsonb")
  private Map<String, Object> output;

  @Type(JsonType.class)
  @JsonProperty("step_input")
  @Column(name = "input", columnDefinition = "jsonb")
  private Map<String, Object> input;

  @Type(JsonType.class)
  @JsonProperty("step_data")
  @Column(name = "data", columnDefinition = "jsonb")
  private Map<String, Object> data;

  @Column(name = "step_limit_execution") // ? same value or include diff value?
  int limit_execution;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_status")
  private STEP_STATUS status;

  @Min(1)
  @Column(name = "step_order")
  int order;

  @Column(name = "step_created_at")
  @JsonProperty("step_created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "step_updated_at")
  @JsonProperty("step_updated_at")
  @UpdateTimestamp
  private Instant updatedAt;

  // JOIN
  // MANY TO ONE
  @JoinTable(
      name = "workflows",
      joinColumns =
          @JoinColumn(
              name = "workflow_id",
              foreignKey =
                  @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_step_workflow")))
  @Column(name = "step_workflow_id")
  private String workflowId;

  // MANY TO MANY
  @JoinTable(
      name = "steps",
      joinColumns = @JoinColumn(name = "step_id"),
      foreignKey =
          @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_step_execution_step_template"))
  @Column(name = "step_template_id")
  private String stepTemplateId;
}
