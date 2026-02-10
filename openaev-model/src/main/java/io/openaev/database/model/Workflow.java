package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "workflows")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Workflow implements Base {

  @Id
  @Column(name = "workflow_id")
  @JsonProperty("workflow_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @Schema(description = "ID of the workflow")
  private String id;

  @NotNull
  @Column(name = "workflow_status")
  @JsonProperty("workflow_status")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "Status of the workflow (TEMPLATE, RUN, STOP, END)")
  private WorkflowStatus status;

  @Min(0)
  @Column(name = "workflow_version")
  @JsonProperty("workflow_version")
  @Schema(description = "Version of the workflow, incremented at each launch if edited")
  private int version;

  @Column(name = "workflow_is_edited")
  @JsonProperty("workflow_is_edited")
  @Schema(description = "Workflow template is edited")
  private boolean isEdited;

  @CreationTimestamp
  @Column(name = "workflow_created_at")
  @JsonProperty("workflow_created_at")
  @Schema(description = "Creation date")
  private Instant workflowCreatedAt;

  @UpdateTimestamp
  @Column(name = "workflow_updated_at")
  @JsonProperty("workflow_updated_at")
  @Schema(description = "Update date")
  private Instant workflowUpdatedAt;

  // JOIN
  @JoinColumn(name = "workflow_template_id")
  @ManyToOne(fetch = FetchType.LAZY)
  @JsonIgnore
  @Schema(description = "Template workflow from which this workflow was created")
  private Workflow workflowTemplate;

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "workflowTemplate")
  @Builder.Default
  @JsonIgnore
  @Schema(description = "Workflows that were executed based on this template workflow")
  private List<Workflow> workflowsExecuted = new ArrayList<>();

  @OneToMany(
      fetch = FetchType.LAZY,
      mappedBy = "workflow",
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Builder.Default
  @JsonProperty("workflow_steps")
  @Schema(description = "Steps that belong to this workflow")
  private List<Step> steps = new ArrayList<>();

  @OneToOne
  @JoinColumn(name = "workflow_simulation_id")
  @JsonIgnore
  @Schema(description = "Simulation associated with this workflow")
  private Exercise simulation;
}
