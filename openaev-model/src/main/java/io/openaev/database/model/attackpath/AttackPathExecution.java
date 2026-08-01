package io.openaev.database.model.attackpath;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One row = one attack-path edge (source → target) for a simulation (issue 6647). Carries the run
 * snapshot: the endpoint/agent/step display attributes are frozen at execution time so a past run
 * renders its state, not today's. Reference ids ({@code simulationId}, asset/agent ids, {@code
 * contractExternalId}) are frozen identity keys, never resolved against the live entities at
 * rebuild.
 */
@Getter
@Setter
@Entity
@Table(name = "attackpath_execution")
@EntityListeners(TenantBaseListener.class)
public class AttackPathExecution implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "attackpath_execution_id")
  private String id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @Column(name = "attackpath_execution_simulation_id", nullable = false)
  private String simulationId;

  @Column(name = "attackpath_execution_step_id")
  private String stepId;

  @Column(name = "attackpath_execution_step_template_id")
  private String stepTemplateId;

  @Column(name = "attackpath_execution_contract_external_id")
  private String contractExternalId;

  // INJECTOR or AGENT
  @Column(name = "attackpath_execution_source_kind", nullable = false)
  private String sourceKind;

  @Column(name = "attackpath_execution_source_asset_id")
  private String sourceAssetId;

  @Column(name = "attackpath_execution_source_hostname")
  private String sourceHostname;

  @Column(name = "attackpath_execution_source_ip")
  private String sourceIp;

  @Column(name = "attackpath_execution_source_platform")
  private String sourcePlatform;

  @Column(name = "attackpath_execution_agent_id")
  private String agentId;

  @Column(name = "attackpath_execution_agent_name")
  private String agentName;

  @Column(name = "attackpath_execution_agent_privilege")
  private String agentPrivilege;

  @Column(name = "attackpath_execution_source_injector")
  private String sourceInjector;

  @Column(name = "attackpath_execution_injector_type")
  private String injectorType;

  // ASSET or DISCOVERED
  @Column(name = "attackpath_execution_target_kind", nullable = false)
  private String targetKind;

  @Column(name = "attackpath_execution_target_asset_id")
  private String targetAssetId;

  @Column(name = "attackpath_execution_target_raw_value")
  private String targetRawValue;

  @Column(name = "attackpath_execution_target_key", nullable = false)
  private String targetKey;

  @Column(name = "attackpath_execution_target_hostname")
  private String targetHostname;

  @Column(name = "attackpath_execution_target_ip")
  private String targetIp;

  @Column(name = "attackpath_execution_target_platform")
  private String targetPlatform;

  @Column(name = "attackpath_execution_payload_name")
  private String payloadName;

  @Column(name = "attackpath_execution_payload_id")
  private String payloadId;

  @Column(name = "attackpath_execution_executed_at", nullable = false)
  private Instant executedAt;

  /**
   * The simulation's {@link AttackPathGraphVersion} value at the write that last touched this row.
   * Every writer stamps it in the same transaction as its bump, so the delta read is a cursor over
   * {@code (simulation_id, row_version)}. Rows written before the versioning existed sit at 0 and
   * are therefore part of any {@code since = 0} delta only.
   */
  @Column(name = "attackpath_execution_row_version", nullable = false)
  private long rowVersion;

  @Column(name = "attackpath_execution_prevention_status")
  private String preventionStatus;

  @Column(name = "attackpath_execution_detection_status")
  private String detectionStatus;

  @Column(name = "attackpath_execution_vulnerability_status")
  private String vulnerabilityStatus;

  /**
   * The graph read never selects it; unlike the heavy TOASTed terminal_output it is generally
   * smaller.
   */
  @Column(name = "attackpath_execution_command")
  private String command;

  /** Heavy; loaded only when a terminal drawer opens, never by the graph read. */
  @Column(name = "attackpath_execution_terminal_output")
  private String terminalOutput;

  public void setGlobalInformation(Step stepExecution, Inject inject) {
    this.tenant = inject.getTenant();
    this.simulationId = inject.getExercise().getId();
    this.stepId = stepExecution.getId();
    this.stepTemplateId = stepExecution.getStepTemplate().getId();
    this.payloadName = inject.getTitle();
    this.executedAt = Instant.now();
    // The graph read never re-reads the live inject; it renders from this frozen row. Freeze the
    // identity keys it resolves from: contractExternalId -> ATT&CK techniques, payloadId ->
    // detection
    // remediations, injectorType -> the injector node's real type.
    // Fall back to the contract id when the external id is blank: built-in contracts (e.g. Nmap)
    // carry only a UUID id, and the graph read resolves the contract via findByIdOrExternalId, so a
    // null here would leave the contract name and ATT&CK unresolved on real runs.
    this.contractExternalId =
        inject
            .getInjectorContract()
            .map(
                contract ->
                    contract.getExternalId() != null && !contract.getExternalId().isBlank()
                        ? contract.getExternalId()
                        : contract.getId())
            .orElse(null);
    this.payloadId = inject.getPayload().map(Payload::getId).orElse(null);
    this.injectorType = inject.getInjector() != null ? inject.getInjector().getType() : null;
  }

  public void setTargetDiscoveredInformation(String key) {
    this.targetKind = "DISCOVERED";
    this.targetKey = key;
    this.targetRawValue = "";
    // TODO if type IP or Hostname
    this.targetHostname = "";
    this.targetIp = "";
  }

  public void setTargetAssetInformation(Endpoint endpoint) {
    this.targetKind = "ASSET";
    this.targetAssetId = endpoint.getId();
    this.targetHostname = endpoint.getHostname();
    this.targetKey = endpoint.getId();
    // Null-safe for the same reason as the source side: endpoint_platform has no NOT NULL
    // constraint and ips can be unset, and the non-fatal ingestion must not drop the row over it.
    this.targetIp = endpoint.getIps() == null ? null : String.join(",", endpoint.getIps());
    this.targetPlatform = endpoint.getPlatform() == null ? null : endpoint.getPlatform().name();
  }

  public void setSourceAgentInformation(Agent agent, Endpoint endpoint) {
    this.sourceKind = "AGENT";
    this.sourceAssetId = agent.getAsset().getId();
    this.sourceHostname = endpoint.getHostname();
    // Null-safe: agent_executor is a nullable column and endpoint_platform has no NOT NULL
    // constraint, so a run's agent/endpoint can lack them. The ingestion is non-fatal, so an
    // unguarded dereference here would silently drop the whole row; keep it with null metadata.
    this.sourceIp = endpoint.getIps() == null ? null : String.join(",", endpoint.getIps());
    this.sourcePlatform = endpoint.getPlatform() == null ? null : endpoint.getPlatform().name();

    this.agentId = agent.getId();
    this.agentName = agent.getExecutor() == null ? null : agent.getExecutor().getName();
    this.agentPrivilege = agent.getPrivilege().name();
  }

  public void setSourceInjectorInformation(Injector injector) {
    this.sourceKind = "INJECTOR";
    this.sourceInjector = injector.getName();
  }

  /**
   * Source = a targeted team, used to hang its recipients (PERSON targets) under the team node so a
   * human-in-the-loop step reads as injector -> team -> persons. The team id rides {@code
   * sourceAssetId} (the same slot an agent/asset source uses) so the graph resolves this node's id
   * exactly as the team's own TARGET row does, converging both onto one team node.
   */
  public void setSourceTeamInformation(String teamId, String teamName) {
    this.sourceKind = "TEAM";
    this.sourceAssetId = teamId;
    this.sourceHostname = teamName;
  }

  /**
   * Target = a team. An email/SMS inject can only target a team, so this is how a human-in-the-loop
   * step lands on the attack path (issue: autonomous phishing rendered an empty graph). The team
   * name rides {@code targetHostname}, the graph read's label fallback, so the node reads as the
   * team, not a bare uuid.
   */
  public void setTargetTeamInformation(String teamId, String teamName) {
    this.targetKind = "TEAM";
    this.targetKey = teamId;
    this.targetHostname = teamName;
    this.targetRawValue = "";
  }

  /** Target = an individual recipient (player) reached through a team. */
  public void setTargetPersonInformation(String userId, String label) {
    this.targetKind = "PERSON";
    this.targetKey = userId;
    this.targetHostname = label;
    this.targetRawValue = "";
  }

  /** Target = an asset group as a whole (its members are separately targeted as ASSET rows). */
  public void setTargetAssetGroupInformation(String assetGroupId, String name) {
    this.targetKind = "ASSET_GROUP";
    this.targetKey = assetGroupId;
    this.targetHostname = name;
    this.targetRawValue = "";
  }
}
