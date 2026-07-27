package io.openaev.database.model.attackpath;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * One row = one simulation's attack-path version counter (#6647, spec 002). The value is bumped
 * inside the same transaction as every projection write, and the same value is stamped on the rows
 * that write touches, so "changes since v" is answerable from the projection tables alone.
 *
 * <p>Deliberately NOT in {@code openaev.tenant.active-tables}, unlike {@code attackpath_execution}
 * and {@code attackpath_finding}: the row holds a counter and nothing else, every read of it sits
 * behind {@code AttackPathAccessControl#assertCanReadSimulation}, and the rows a delta actually
 * returns stay inspector-scoped. Activating it would force the bump upsert (which the inspector
 * cannot rewrite) onto raw JDBC, which is forbidden on an active table. {@code tenant_id} is still
 * NOT NULL with an indexed cascading FK, so a deleted tenant takes its counters with it.
 */
@Getter
@Setter
@Entity
@Table(name = "attackpath_graph_version")
@EntityListeners(TenantBaseListener.class)
public class AttackPathGraphVersion implements TenantBase {

  @Id
  @Column(name = "attackpath_graph_version_simulation_id")
  private String simulationId;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @Column(name = "attackpath_graph_version_value", nullable = false)
  private long value;

  /** The simulation id IS the identity: one counter per simulation, no surrogate key. */
  @Override
  @JsonIgnore
  public String getId() {
    return this.simulationId;
  }

  @Override
  public void setId(String id) {
    this.simulationId = id;
  }

  /** A counter is not a domain event: never stream a bump to connected clients. */
  @Override
  public boolean isListened() {
    return false;
  }
}
