package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathGraphVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * The per-simulation attack-path version counter (#6647, spec 002). Two statements, both meant to
 * run inside a writer's existing transaction: {@link #bump} takes the next version, {@link
 * #findValue} reads it back.
 */
public interface AttackPathGraphVersionRepository
    extends CrudRepository<AttackPathGraphVersion, String> {

  /**
   * Takes the simulation's next version: creates the counter at 1 or increments it. Native because
   * the upsert is not expressible in JPQL; it goes through Hibernate (never raw JDBC), and the
   * table is not tenant-active, so the statement inspector passes it through untouched.
   *
   * <p>The {@code ON CONFLICT DO UPDATE} holds a row lock until the surrounding transaction
   * commits. That is the whole point: a second writer on the same simulation blocks there, so
   * version order equals commit order and a client can never see version <i>w</i> before the rows
   * of <i>w</i>. {@code tenant_id} is written explicitly.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          "INSERT INTO attackpath_graph_version (attackpath_graph_version_simulation_id, tenant_id,"
              + " attackpath_graph_version_value) VALUES (:simulationId, :tenantId, 1)"
              + " ON CONFLICT (attackpath_graph_version_simulation_id) DO UPDATE SET"
              + " attackpath_graph_version_value ="
              + " attackpath_graph_version.attackpath_graph_version_value + 1")
  void bump(@Param("simulationId") String simulationId, @Param("tenantId") String tenantId);

  /**
   * The simulation's current version, or empty when it has no attack-path data (never ingested, or
   * reset). Empty with a {@code since > 0} is what tells the delta read to demand a resync.
   */
  @Query("SELECT v.value FROM AttackPathGraphVersion v WHERE v.simulationId = :simulationId")
  Optional<Long> findValue(@Param("simulationId") String simulationId);

  /** Drops a simulation's counter, on attack-path reset and delete. */
  void deleteBySimulationId(String simulationId);
}
