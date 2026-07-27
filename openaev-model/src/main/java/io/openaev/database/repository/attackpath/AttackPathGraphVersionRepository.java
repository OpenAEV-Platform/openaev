package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathGraphVersion;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * The per-simulation attack-path version counter (#6647, spec 002). Three statements, all meant to
 * run inside their caller's existing transaction: {@link #bump} takes the next version, {@link
 * #findValue} reads it, {@link #deleteBySimulationId} drops it.
 *
 * <p>The table is not tenant-active (the upsert is a shape the inspector cannot rewrite), so every
 * statement here carries its own tenant predicate and the counter is keyed by {@code
 * (simulation_id, tenant_id)}. That is the whole isolation of this table: a statement added without
 * a tenant predicate would read straight across tenants.
 */
public interface AttackPathGraphVersionRepository
    extends CrudRepository<AttackPathGraphVersion, String> {

  /**
   * Takes the simulation's next version and returns it: creates the counter at 1 or increments it,
   * in ONE statement. Native because the upsert is not expressible in JPQL, and {@code RETURNING}
   * rather than a follow-up read because a second round-trip could observe another writer's
   * increment and hand this writer a version it never stamped on its rows.
   *
   * <p>The {@code ON CONFLICT DO UPDATE} holds the counter's row lock until the surrounding
   * transaction commits. That is the point: a second writer on the same simulation blocks there, so
   * version order equals commit order and a client can never see version <i>w</i> before the rows
   * of <i>w</i>. The conflict target is the composite key, so two tenants sharing a simulation id
   * keep separate counters.
   */
  @Query(
      nativeQuery = true,
      value =
          "INSERT INTO attackpath_graph_version (attackpath_graph_version_simulation_id, tenant_id,"
              + " attackpath_graph_version_value) VALUES (:simulationId, :tenantId, 1)"
              + " ON CONFLICT (attackpath_graph_version_simulation_id, tenant_id) DO UPDATE SET"
              + " attackpath_graph_version_value ="
              + " attackpath_graph_version.attackpath_graph_version_value + 1"
              + " RETURNING attackpath_graph_version_value")
  Long bump(@Param("simulationId") String simulationId, @Param("tenantId") String tenantId);

  /**
   * The simulation's current version within the caller's tenant scope, or empty when it has no
   * attack-path data there (never ingested, reset, or another tenant's simulation). Empty with a
   * {@code since > 0} is what tells the delta read to demand a resync.
   */
  @Query(
      "SELECT v.value FROM AttackPathGraphVersion v "
          + "WHERE v.simulationId = :simulationId AND v.tenant.id IN :tenantIds")
  Optional<Long> findValue(
      @Param("simulationId") String simulationId, @Param("tenantIds") Collection<String> tenantIds);

  /** Drops a simulation's counter in one tenant, on attack-path reset and delete. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "DELETE FROM AttackPathGraphVersion v "
          + "WHERE v.simulationId = :simulationId AND v.tenant.id = :tenantId")
  void deleteBySimulationId(
      @Param("simulationId") String simulationId, @Param("tenantId") String tenantId);
}
