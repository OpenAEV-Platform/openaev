package io.openaev.database.model.attackpath;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

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

  @Column(name = "attackpath_execution_inject_id")
  private String injectId;

  @Column(name = "attackpath_execution_step_id")
  private String stepId;

  @Column(name = "attackpath_execution_step_template_id")
  private String stepTemplateId;

  @Column(name = "attackpath_execution_contract_external_id")
  private String contractExternalId;

  //INJECTOR or AGENT
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

  //ASSET or DISCOVERED
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

  @Column(name = "attackpath_execution_prevention_status")
  private String preventionStatus;

  @Column(name = "attackpath_execution_detection_status")
  private String detectionStatus;

  @Column(name = "attackpath_execution_vulnerability_status")
  private String vulnerabilityStatus;

  /** Heavy; TOAST keeps it off-row and the graph read never selects it. */
  @Column(name = "attackpath_execution_command")
  private String command;

  /** Heavy; loaded only when a terminal drawer opens, never by the graph read. */
  @Column(name = "attackpath_execution_terminal_output")
  private String terminalOutput;

  public void setGlobalInformation(Step stepExecution, Inject inject) {
    this.tenant = inject.getTenant();
    this.simulationId =inject.getExercise().getId();
    this.injectId = inject.getId();
    this.stepId = stepExecution.getId();
    this.stepTemplateId = stepExecution.getStepTemplate().getId();
    this.payloadName = inject.getTitle();
    this.executedAt = Instant.now();
    // The graph read never re-reads the live inject; it renders from this frozen row. Freeze the
    // identity keys it resolves from: contractExternalId -> ATT&CK techniques, payloadId -> detection
    // remediations, injectorType -> the injector node's real type.
    this.contractExternalId =
        inject.getInjectorContract().map(InjectorContract::getExternalId).orElse(null);
    this.payloadId = inject.getPayload().map(Payload::getId).orElse(null);
    this.injectorType = inject.getInjector() != null ? inject.getInjector().getType() : null;
    }

  public void setTargetDiscoveredInformation(String key) {
    this.targetKind = "DISCOVERED";
    this.targetKey = key;
    this.targetRawValue = "";
    //TODO if type IP or Hostname
    this.targetHostname = "";
    this.targetIp = "";
  }

  public void setTargetAssetInformation(Endpoint endpoint) {
    this.targetKind = "ASSET";
    this.targetAssetId = endpoint.getId();
    this.targetHostname = endpoint.getHostname();
    this.targetKey = endpoint.getId();
    this.targetIp = String.join(",", endpoint.getIps());
    this.targetPlatform = endpoint.getPlatform().name();
  }

  public void setSourceAgentInformation(Agent agent, Endpoint endpoint) {
    this.sourceKind ="AGENT";
    this.sourceAssetId = agent.getAsset().getId();
    this.sourceHostname = endpoint.getHostname();
    this.sourceIp = String.join(",", endpoint.getIps());
    this.sourcePlatform = endpoint.getPlatform().name();

    this.agentId = agent.getId();
    this.agentName = agent.getExecutor().getName();
    this.agentPrivilege = agent.getPrivilege().name();

  }

  public void setSourceInjectorInformation(Injector injector) {
    this.sourceKind ="INJECTOR";
    this.sourceInjector = injector.getName();
  }
}

