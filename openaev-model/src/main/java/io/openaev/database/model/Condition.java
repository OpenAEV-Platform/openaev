package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
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
@Builder
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
  @JsonProperty("condition_workflow_id")
  @Schema(description = "Workflow id related to the condition")
  private String workflowId;

  @Column(name = "condition_key")
  @JsonProperty("condition_key")
  @Schema(description = "Condition key")
  private String key;

  @org.hibernate.annotations.Type(JsonType.class)
  @Column(name = "condition_key_types", columnDefinition = "jsonb")
  @JsonProperty("condition_key_types")
  @Schema(description = "Key types")
  private List<PrimitiveType> keyTypes;

  @Column(name = "condition_type")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("condition_type")
  @Schema(description = "Type")
  private ConditionType type;

  @Column(name = "condition_mapping_type")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("condition_mapping_type")
  @Schema(description = "Mapping type, ex: DEFAULT, LOCAL or GLOBAL")
  private MappingType mappingType;

  @Column(name = "condition_value")
  @JsonProperty("condition_value")
  @Schema(description = "Value")
  private String value;

  @Column(name = "condition_case_sensitive")
  @Schema(description = "Whether the comparison is case-sensitive")
  @Builder.Default
  private boolean caseSensitive = true;

  @Column(name = "condition_name")
  @JsonProperty("condition_name")
  @Schema(description = "Name")
  private String name;

  @Column(name = "condition_description")
  @JsonProperty("condition_description")
  @Schema(description = "Description")
  private String description;

  @OneToMany(
      fetch = FetchType.LAZY,
      mappedBy = "condition",
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonIgnore
  @Schema(description = "Step links attached to this condition")
  @Builder.Default
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
  @Builder.Default
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
  @JsonProperty("condition_created_at")
  @Schema(description = "Creation timestamp")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "condition_updated_at")
  @JsonProperty("condition_updated_at")
  @Schema(description = "Last update timestamp")
  private Instant updateDate;

  // -- COMPUTED EXPORT PROPERTIES --

  /** Returns the parent condition ID, or null if this is a root condition. */
  @JsonProperty("condition_parent_id")
  public String getConditionParentId() {
    return conditionParent != null ? conditionParent.getId() : null;
  }

  /** Returns the source step ID, or null if no source step. */
  @JsonProperty("condition_step_from_id")
  public String getStepFromId() {
    return stepFrom != null ? stepFrom.getId() : null;
  }

  /** Returns true if this condition is a root (has no parent). */
  @JsonProperty("condition_is_root")
  public boolean isRootCondition() {
    return conditionParent == null;
  }
}
