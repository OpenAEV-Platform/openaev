package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.OnDelete;
import io.openaev.annotation.OnDeleteAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.*;
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
  @OnDelete(action = OnDeleteAction.SET_REFERENCE_NULL, fieldName = "workflowTemplate")
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

  // -- Scope --

  // Rate limit
  @Column(name = "workflow_rate_limit_enabled", columnDefinition = "boolean")
  @JsonProperty("workflow_rate_limit_enabled")
  private boolean rateLimitEnabled;

  @Column(name = "workflow_max_attempts")
  @JsonProperty("workflow_max_attempts")
  @Min(1)
  @Max(99)
  private Integer maxAttempts;

  @Column(name = "workflow_max_temporal_rate_seconds")
  @JsonProperty("workflow_max_temporal_rate_seconds")
  @Min(1)
  @Max(5940) // 99 min
  private Long maxTemporalRateSeconds;

  // Timeout
  @Column(name = "workflow_timeout_enabled", columnDefinition = "boolean")
  @JsonProperty("workflow_timeout_enabled")
  private boolean timeoutEnabled;

  @Column(name = "workflow_timeout_seconds")
  @JsonProperty("workflow_timeout_seconds")
  @Min(0)
  @Max(86400) // 24h
  private Long timeoutSeconds;

  // Safe mode
  @Column(name = "workflow_safe_mode_enabled", columnDefinition = "boolean")
  @JsonProperty("workflow_safe_mode_enabled")
  private boolean safeModeEnabled;

  // Rules
  @OneToMany(
      mappedBy = "workflow",
      fetch = FetchType.LAZY,
      orphanRemoval = true,
      cascade = CascadeType.ALL)
  @Builder.Default
  @JsonProperty("workflow_scope_rules")
  private List<WorkflowScopeRule> workflowScopeRules = new ArrayList<WorkflowScopeRule>();

  @JsonIgnore
  public List<WorkflowScopeRule> getAllowlist() {
    return this.workflowScopeRules.stream()
        .filter(r -> ScopeRuleSelectedMode.ALLOWLIST.equals(r.getSelectedMode()))
        .toList();
  }

  @JsonIgnore
  public List<WorkflowScopeRule> getDenylist() {
    return this.workflowScopeRules.stream()
        .filter(r -> ScopeRuleSelectedMode.DENYLIST.equals(r.getSelectedMode()))
        .toList();
  }

  @OneToOne
  @JoinColumn(name = "workflow_scenario_id")
  @JsonIgnore
  @Schema(description = "Scenario associated with this workflow")
  private Scenario scenario;

  // Variables
  @OneToMany(
      mappedBy = "workflow",
      fetch = FetchType.LAZY,
      orphanRemoval = true,
      cascade = CascadeType.ALL)
  @Builder.Default
  @JsonProperty("workflow_scope_variables")
  private List<ScopeVariable> workflowScopeVariables = new ArrayList<>();
}
