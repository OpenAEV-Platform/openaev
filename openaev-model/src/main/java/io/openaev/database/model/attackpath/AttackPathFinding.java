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
import lombok.Getter;
import lombok.Setter;

/**
 * One row = one (endpoint, type, value) finding for a simulation (issue 6647). {@code simulationId}
 * is denormalized so per-simulation reads and the expand index stay tight. The render dedups a
 * finding node by (type, value); the row stays per-endpoint, so the same value on two endpoints is
 * two rows but one node.
 */
@Getter
@Setter
@Entity
@Table(name = "attackpath_finding")
@EntityListeners(TenantBaseListener.class)
public class AttackPathFinding implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "attackpath_finding_id")
  private String id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @Column(name = "attackpath_finding_simulation_id", nullable = false)
  private String simulationId;

  @Column(name = "attackpath_finding_type", nullable = false)
  private String type;

  @Column(name = "attackpath_finding_field")
  private String field;

  @Column(name = "attackpath_finding_value", nullable = false)
  private String value;

  @Column(name = "attackpath_finding_endpoint_id")
  private String endpointId;

  @Column(name = "attackpath_finding_endpoint_raw")
  private String endpointRaw;

  @Column(name = "attackpath_finding_endpoint_key", nullable = false)
  private String endpointKey;

  /**
   * The simulation's {@link AttackPathGraphVersion} value at the write that created this row,
   * stamped in the same transaction as the bump so the delta read is a cursor over {@code
   * (simulation_id, row_version)}. The copy is insert-only ({@code ON CONFLICT DO NOTHING}), so a
   * re-copied identical finding keeps its original version: nothing changed, nothing to ship. A
   * future writer that UPDATES a finding row must re-stamp this column, or its change will never
   * reach a client.
   */
  @Column(name = "attackpath_finding_row_version", nullable = false)
  private long rowVersion;
}
