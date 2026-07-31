package io.openaev.database.model.autonomous;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One AI-driven, autonomous attack-path run. The "brain" lives in XTM One; this row is OpenAEV's
 * durable handle on that run: it binds the objective, the chained simulation used as the execution
 * and visualization substrate, the XTM One session, and the live status the UI animates.
 */
@Getter
@Setter
@Entity
@Table(name = "autonomous_runs")
@EntityListeners(TenantBaseListener.class)
public class AutonomousRun implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "autonomous_run_id")
  @JsonProperty("autonomous_run_id")
  @Schema(description = "ID of the autonomous run")
  private String id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @NotNull
  @Column(name = "autonomous_run_objective", nullable = false, columnDefinition = "text")
  @JsonProperty("autonomous_run_objective")
  @Schema(description = "Free-text or template-derived objective for the run")
  private String objective;

  @Column(name = "autonomous_run_objective_template_key")
  @JsonProperty("autonomous_run_objective_template_key")
  @Schema(description = "Key of the objective template the run was seeded from, if any")
  private String objectiveTemplateKey;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "autonomous_run_status", nullable = false)
  @JsonProperty("autonomous_run_status")
  @Schema(description = "Lifecycle status of the run")
  private AutonomousRunStatus status = AutonomousRunStatus.CREATED;

  @Column(name = "autonomous_run_simulation_id")
  @JsonProperty("autonomous_run_simulation_id")
  @Schema(description = "Chained simulation (Exercise) this run drives")
  private String simulationId;

  @Column(name = "autonomous_run_scenario_id")
  @JsonProperty("autonomous_run_scenario_id")
  @Schema(description = "Scenario the simulation was created from, if any")
  private String scenarioId;

  @Column(name = "autonomous_run_scope_asset_group_id")
  @JsonProperty("autonomous_run_scope_asset_group_id")
  @Schema(description = "Asset group defining the initial in-scope perimeter")
  private String scopeAssetGroupId;

  @Column(name = "autonomous_run_xtm_session_id")
  @JsonProperty("autonomous_run_xtm_session_id")
  @Schema(description = "XTM One orchestrator session id for streaming reconnection")
  private String xtmSessionId;

  @Column(name = "autonomous_run_xtm_agent_slug")
  @JsonProperty("autonomous_run_xtm_agent_slug")
  @Schema(description = "XTM One orchestrator agent slug")
  private String xtmAgentSlug;

  @Column(name = "autonomous_run_last_error", columnDefinition = "text")
  @JsonProperty("autonomous_run_last_error")
  @Schema(description = "Last error message when the run failed")
  private String lastError;

  @CreationTimestamp
  @Column(name = "autonomous_run_created_at", updatable = false)
  @JsonProperty("autonomous_run_created_at")
  @Schema(description = "Creation date")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "autonomous_run_updated_at")
  @JsonProperty("autonomous_run_updated_at")
  @Schema(description = "Update date")
  private Instant updatedAt;
}
