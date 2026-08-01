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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

  @Column(name = "autonomous_run_scope_team_id")
  @JsonProperty("autonomous_run_scope_team_id")
  @Schema(
      description =
          "First team of the scope, projected for convenience. Authoritative scope is the mixed"
              + " list in autonomous_run_scope. An inject can only target a team, never a bare"
              + " person.")
  private String scopeTeamId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "autonomous_run_scope")
  @JsonProperty("autonomous_run_scope")
  @Schema(
      description =
          "Authoritative run scope: a mixed list of targetable entities (assets, asset groups,"
              + " teams, persons). The orchestrator attacks within this perimeter.")
  private List<AutonomousScopeTarget> scope = new ArrayList<>();

  // Internal bookkeeping: maps each step template id authored on the SIMULATION workflow to the
  // twin step template id mirrored onto the SCENARIO workflow. The orchestrator only ever knows the
  // simulation step ids (those are what appendChainedStep returns), so when it authors a step that
  // DEPEND_ONs a simulation parent, this lets us reattach the scenario twin to the scenario parent -
  // keeping the mirrored attack path's kill-chain ordering intact for export. Never exposed to the
  // API or the orchestrator.
  @JsonIgnore
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "autonomous_run_step_mirror")
  private Map<String, String> stepMirror = new HashMap<>();

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
