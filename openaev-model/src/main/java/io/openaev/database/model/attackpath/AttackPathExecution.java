package io.openaev.database.model.attackpath;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

  @Column(name = "attackpath_execution_inject_id")
  private String injectId;

  @Column(name = "attackpath_execution_step_id")
  private String stepId;

  @Column(name = "attackpath_execution_step_template_id")
  private String stepTemplateId;

  @Column(name = "attackpath_execution_contract_external_id")
  private String contractExternalId;

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

  /** The graph read never selects it; unlike the heavy TOASTed terminal_output it is generally smaller. */
  @Column(name = "attackpath_execution_command")
  private String command;

  /** Heavy; loaded only when a terminal drawer opens, never by the graph read. */
  @Column(name = "attackpath_execution_terminal_output")
  private String terminalOutput;
}
