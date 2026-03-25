package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Condition implements Base {

  @Id
  @Column(name = "condition_id")
  @JsonProperty("condition_id")
  @GeneratedValue
  @UuidGenerator
  @EqualsAndHashCode.Include
  @Schema(description = "ID of the condition")
  private String id;

  @Column(name = "condition_workflow_id")
  @Schema(description = "Workflow id related to the condition")
  private String workflowId;

  @Column(name = "condition_key_type")
  @Schema(description = "Key type")
  private String keyType;

  @Column(name = "condition_type")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "Type")
  private ConditionType type;

  @Column(name = "condition_value")
  @Schema(description = "Value")
  private String value;

  @Column(name = "condition_name")
  @Schema(description = "Name")
  private String name;

  @Column(name = "condition_description")
  @Schema(description = "Description")
  private String description;

  @OneToMany(
      fetch = FetchType.LAZY,
      mappedBy = "condition",
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonIgnore
  @Schema(description = "Step links attached to this condition")
  private List<ConditionStep> conditionSteps = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "condition_parent_id")
  @JsonIgnore
  private Condition conditionParent;

  @OneToMany(
      fetch = FetchType.LAZY,
      mappedBy = "conditionParent",
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonIgnore
  @Schema(description = "Child conditions of this condition")
  private List<Condition> conditionChildren = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "step_from_id",
      foreignKey = @ForeignKey(name = "conditions_step_from_id_fkey"))
  @JsonIgnore
  @Schema(description = "Source step for this condition")
  private Step stepFrom;

  @CreationTimestamp
  @Column(name = "condition_created_at", updatable = false)
  @Schema(description = "Creation timestamp")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "condition_updated_at")
  @Schema(description = "Last update timestamp")
  private Instant updateDate;

  private static final String KEY_TYPE_EXECUTION_TIME = "execution_time";
  private static final String KEY_TYPE_STEP_TEMPLATE_ID = "step_template_id";

  public boolean isRoot() {
    return conditionParent == null;
  }

  public static Condition executionOf(Condition source, Object goal) {
    Objects.requireNonNull(source, "source condition must not be null");
    Objects.requireNonNull(source.getType(), "source condition type must not be null");
    return conditionOf(
        source.getType(), KEY_TYPE_EXECUTION_TIME, goal != null ? goal.toString() : null);
  }

  public static Condition dependOn(String stepTemplateId) {
    if (stepTemplateId == null || stepTemplateId.isBlank()) {
      throw new IllegalArgumentException("stepTemplateId must not be null or blank");
    }
    return conditionOf(ConditionType.DEPEND_ON, KEY_TYPE_STEP_TEMPLATE_ID, stepTemplateId);
  }

  private static Condition conditionOf(ConditionType type, String keyType, String value) {
    Condition condition = new Condition();
    condition.setType(Objects.requireNonNull(type, "condition type must not be null"));
    condition.setKeyType(Objects.requireNonNull(keyType, "condition keyType must not be null"));
    condition.setValue(value);
    return condition;
  }

  /**
   * Backward-compatible accessor used by existing chaining code. Returns the step linked with
   * is_root=true when present, otherwise the first linked step.
   */
  @JsonIgnore
  public Step getStep() {
    return conditionSteps.stream()
        .filter(ConditionStep::isRoot)
        .map(ConditionStep::getStep)
        .findFirst()
        .orElseGet(
            () -> conditionSteps.stream().map(ConditionStep::getStep).findFirst().orElse(null));
  }

  /** Backward-compatible mutator used by existing chaining code. */
  public void setStep(Step step) {
    linkToStep(step, true);
  }

  /** Creates or updates a link between this condition and a step. */
  public void linkToStep(Step step, boolean isRoot) {
    if (step == null) {
      return;
    }

    ConditionStep existingLink =
        conditionSteps.stream()
            .filter(link -> link.getStep() != null)
            .filter(link -> Objects.equals(link.getStep().getId(), step.getId()))
            .findFirst()
            .orElse(null);

    if (existingLink != null) {
      existingLink.setRoot(isRoot);
      return;
    }

    ConditionStep link = new ConditionStep();
    link.setCondition(this);
    link.setStep(step);
    link.setRoot(isRoot);
    conditionSteps.add(link);
  }

  /** Removes the link to the provided step id if present. */
  public void unlinkFromStep(String stepId) {
    if (stepId == null || conditionSteps == null || conditionSteps.isEmpty()) {
      return;
    }
    conditionSteps.removeIf(
        link -> link.getStep() != null && Objects.equals(link.getStep().getId(), stepId));
  }
}
