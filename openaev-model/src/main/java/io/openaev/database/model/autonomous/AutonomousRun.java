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

  @NotNull
  @Column(name = "autonomous_run_plan_mode", nullable = false)
  @JsonProperty("autonomous_run_plan_mode")
  @Schema(
      description =
          "Build flag. When true the orchestrator only authors the scenario's logic (scope + steps +"
              + " decisions) and nothing is executed; the built logic is shown in draft orange and"
              + " can then be launched (in normal or autonomous mode).")
  private boolean planMode = false;

  @Column(name = "autonomous_run_plan_guidance", columnDefinition = "text")
  @JsonProperty("autonomous_run_plan_guidance")
  @Schema(
      description =
          "Plan summary captured while building the logic and handed to a subsequent live autonomous"
              + " run as guidance, so the live run follows the plan while still adapting to what it"
              + " finds.")
  private String planGuidance;

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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "autonomous_run_agent_ids")
  @JsonProperty("autonomous_run_agent_ids")
  @Schema(
      description =
          "XTM One agent ids the orchestrator may consult as specialist handover targets during"
              + " this run (in addition to the built-in payload creator).")
  private List<String> agentIds = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "autonomous_run_agent_modes")
  @JsonProperty("autonomous_run_agent_modes")
  @Schema(
      description =
          "Per-agent discovery mode for this run: maps an XTM One agent id (or the orchestrator's"
              + " own id) to how much latitude it has to bring newly discovered entities into the"
              + " attack path (EXISTING_ONLY / SCOPED / EXPANSIVE). Enforced at OpenAEV's creation"
              + " choke points against the acting agent. An agent absent from the map falls back to"
              + " SCOPED.")
  private Map<String, String> agentModes = new HashMap<>();

  // Internal bookkeeping: maps each step template id authored on the SIMULATION workflow to the
  // twin step template id mirrored onto the SCENARIO workflow. The orchestrator only ever knows
  // the simulation step ids (those are what appendChainedStep returns), so when it authors a step
  // that DEPEND_ONs a simulation parent, this lets us reattach the scenario twin to the scenario
  // parent, keeping the mirrored attack path's kill-chain ordering intact for export. Never
  // exposed to the API or the orchestrator.
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

  @Column(name = "autonomous_run_timeout_seconds")
  @JsonProperty("autonomous_run_timeout_seconds")
  @Schema(
      description =
          "Maximum wall-clock lifetime of the run in seconds. OpenAEV owns this deadline: it steers"
              + " the orchestrator with winddown signals shortly before it, then hard-stops the run"
              + " (exactly like an operator Stop) when it is reached. Null means no OpenAEV-enforced"
              + " timeout (e.g. build mode).")
  private Long timeoutSeconds;

  @Column(name = "autonomous_run_started_at")
  @JsonProperty("autonomous_run_started_at")
  @Schema(
      description = "When the run was last moved to RUNNING; the timeout deadline is based on it")
  private Instant startedAt;

  @Column(name = "autonomous_run_deadline_at")
  @JsonProperty("autonomous_run_deadline_at")
  @Schema(
      description =
          "Absolute instant at which OpenAEV hard-stops the run. Computed from startedAt +"
              + " timeoutSeconds when the run becomes live. Null when no timeout applies.")
  private Instant deadlineAt;

  // Internal bookkeeping: which winddown steering signal the timeout watchdog has already queued
  // for
  // this run, so it emits each nudge at most once. Null -> none, "WINDDOWN_5M" -> 5-minute signal
  // sent, "WINDDOWN_1M" -> 1-minute signal sent. Reset whenever the run (re)enters RUNNING. Never
  // exposed to the API or the orchestrator.
  @JsonIgnore
  @Column(name = "autonomous_run_winddown_phase")
  private String winddownPhase;
}
