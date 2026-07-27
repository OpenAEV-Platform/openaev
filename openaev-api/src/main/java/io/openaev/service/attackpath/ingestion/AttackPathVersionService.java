package io.openaev.service.attackpath.ingestion;

import io.openaev.database.repository.attackpath.AttackPathGraphVersionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The attack-path version primitive (#6647, spec 002): one monotonic counter per simulation, bumped
 * by every writer that touches the projection, and stamped by that writer on every row it writes.
 *
 * <p>Two rules the callers must keep, because they are what the delta contract rests on: <b>no bump
 * outside a projection-write transaction, and no projection write without a bump in the same
 * transaction</b>. Break the first and clients burn a poll on an empty delta; break the second and
 * a client can hold a version whose rows it has never been sent — a silently wrong graph until the
 * next unrelated write.
 *
 * <p>Own bean rather than a method on each ingestion service: a self-invocation would bypass the
 * Spring proxy, and every caller already runs inside a tenant-scoped transaction opened with {@code
 * TenantScopedTransaction#executeNew}, which this service joins.
 */
@Service
@RequiredArgsConstructor
public class AttackPathVersionService {

  private final AttackPathGraphVersionRepository versionRepository;

  /**
   * Takes the simulation's next version and returns it, to stamp on the rows of the same write.
   * Must be called inside the writer's transaction: the upsert's row lock is released at commit,
   * and that is what keeps version order equal to commit order for concurrent writers on one
   * simulation.
   */
  public long bump(String simulationId, String tenantId) {
    versionRepository.bump(simulationId, tenantId);
    return versionRepository.findValue(simulationId).orElse(0L);
  }

  /** The simulation's current version, or empty when it has no attack-path data (or was reset). */
  public Optional<Long> current(String simulationId) {
    return versionRepository.findValue(simulationId);
  }

  /**
   * Drops the simulation's counter, on attack-path reset and delete. A client still polling with an
   * old {@code since} then finds no counter, which the delta read answers with a resync — the only
   * shape in which the contract expresses a deletion.
   */
  public void deleteBySimulationId(String simulationId) {
    versionRepository.deleteBySimulationId(simulationId);
  }
}
