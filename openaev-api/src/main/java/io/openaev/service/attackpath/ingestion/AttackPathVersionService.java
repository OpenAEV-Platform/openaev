package io.openaev.service.attackpath.ingestion;

import io.openaev.database.repository.attackpath.AttackPathGraphVersionRepository;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Takes the simulation's next version and returns it, to stamp on the rows of the same write. One
   * atomic statement: the increment and the value the caller stamps come from the same upsert, so
   * no concurrent writer can slip between them and leave this writer stamping a version it did not
   * take. Must be called inside the writer's transaction — the upsert's row lock is released at
   * commit, and that is what keeps version order equal to commit order.
   *
   * @throws IllegalStateException when the upsert returns no value. There is no fallback to 0:
   *     stamping 0 would mark the rows as pre-versioning, hence invisible to every {@code since >
   *     0} cursor.
   */
  public long bump(String simulationId, String tenantId) {
    Long version = versionRepository.bump(simulationId, tenantId);
    if (version == null) {
      throw new IllegalStateException(
          "Attack-path version bump returned no value for simulation "
              + simulationId
              + "; refusing to stamp rows with an unknown version");
    }
    // The nudge (spec 003, FR1): published inside the writer's transaction, delivered after commit
    // by the stream's @TransactionalEventListener, so a client fetching on it never observes a
    // version lower than the announced one. Every projection writer bumps here, so this single
    // point covers them all. Several bumps in one transaction yield several nudges, which is
    // harmless: the delta fetch is cursor-idempotent and the client debounces bursts.
    eventPublisher.publishEvent(new AttackPathVersionEvent(simulationId, tenantId, version));
    return version;
  }

  /**
   * The simulation's current version within the caller's tenant scope, or empty when it has no
   * attack-path data there (never ingested, reset, or another tenant's simulation). An empty scope
   * reads as absent, so a scope-less read fails closed like the inspector-filtered ones.
   */
  public Optional<Long> current(String simulationId, Collection<String> tenantIds) {
    return tenantIds.isEmpty()
        ? Optional.empty()
        : versionRepository.findValue(simulationId, tenantIds);
  }

  /**
   * Drops the simulation's counter in its tenant, on attack-path reset and delete. A client still
   * polling with an old {@code since} then finds no counter, which the delta read answers with a
   * resync — the only shape in which the contract expresses a deletion.
   */
  public void deleteBySimulationId(String simulationId, String tenantId) {
    versionRepository.deleteBySimulationId(simulationId, tenantId);
  }
}
