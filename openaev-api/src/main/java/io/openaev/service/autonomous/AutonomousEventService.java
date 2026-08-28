package io.openaev.service.autonomous;

import io.openaev.database.model.Tenant;
import io.openaev.database.model.autonomous.AutonomousEvent;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends AI-decision-timeline events for an autonomous run and nudges the attack-path SSE so open
 * views fetch both the graph delta and the new timeline entries immediately.
 *
 * <p>Each append bumps the simulation's attack-path version and publishes a change nudge inside the
 * same transaction. A bump that writes no projection rows is a valid state the delta read already
 * handles (it is the same shape as the chaining engine's replay bump); autonomous decisions land at
 * a human-review cadence, not the per-replay flood the version service warns against, so publishing
 * here is safe and is exactly how the live view animates without a second SSE channel.
 *
 * <p>The tenant travels EXPLICITLY through every append ({@code autonomous_events} is
 * tenant-active, so the row must be attributed at creation): callers pass the parent run's tenant,
 * and the same id drives the attack-path nudge. The previous {@code TenantContext} thread-local
 * defaulted to the default tenant on the orchestrator's non-prefixed callback route, which would
 * have both mis-attributed rows and nudged the wrong tenant's SSE channel.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutonomousEventService {

  private final AutonomousEventRepository eventRepository;
  private final AttackPathVersionService attackPathVersionService;

  /**
   * Titles that narrate a run reaching its END. Kept in one place because it is the set the
   * "narrate the end once" guard ({@link #appendTerminalStatusOnce}) matches against - a run's
   * lifetime has exactly one of these, and every terminal-settle path (operator Stop, timeout
   * watchdog, read-path reconcile) must funnel its terminal line through the guard so a resurrected
   * + re-settled run can never spam a second identical one.
   */
  public static final Set<String> TERMINAL_STATUS_TITLES =
      Set.of("Run canceled", "Run completed", "Run timed out", "Run failed", "Run stalled");

  /**
   * High 32 bits of the per-run advisory-lock key, namespacing autonomous event-sequence locks so
   * they can never collide with any other advisory lock the platform might take. The value is
   * arbitrary but stable ("AE" = autonomous event).
   */
  private static final long EVENT_SEQUENCE_LOCK_NAMESPACE = 0x4145_0001L;

  @Transactional(rollbackFor = Exception.class)
  public AutonomousEvent append(
      String runId,
      String tenantId,
      String simulationId,
      AutonomousEventType type,
      String title,
      String content,
      String data) {
    return doAppend(runId, tenantId, simulationId, type, title, content, data);
  }

  /**
   * Body of {@link #append}. Kept un-annotated so {@link #appendTerminalStatusOnce} can reuse it
   * without the intra-class {@code @Transactional} self-invocation trap (a same-class call bypasses
   * the Spring proxy). Must be called inside an active transaction.
   */
  private AutonomousEvent doAppend(
      String runId,
      String tenantId,
      String simulationId,
      AutonomousEventType type,
      String title,
      String content,
      String data) {
    // Serialise concurrent appenders to THIS run before the read-max-then-insert below: two
    // decision cycles (or a cycle racing an operator directive) writing to the same run must not
    // both compute the same next sequence. The transaction advisory lock makes both succeed as N
    // and N+1; the UNIQUE (run_id, sequence) index is the backstop that makes a duplicate
    // impossible regardless. Re-acquiring the same key inside one transaction (the terminal-once
    // path locks before its existence check, then reaches this) is a cheap no-op.
    eventRepository.lockRunEventSequence(sequenceLockKey(runId));
    AutonomousEvent event = new AutonomousEvent();
    event.setTenant(new Tenant(tenantId));
    event.setRunId(runId);
    event.setSequence(eventRepository.findMaxSequence(runId) + 1);
    event.setType(type);
    event.setTitle(title);
    event.setContent(content);
    event.setData(data);
    AutonomousEvent saved = eventRepository.save(event);

    // Nudge the attack-path SSE so the live view refreshes graph + timeline together, under the
    // run's own tenant (the caller-supplied id, never a thread-local default).
    if (simulationId != null && !simulationId.isBlank()) {
      try {
        long version = attackPathVersionService.bump(simulationId, tenantId);
        attackPathVersionService.publishChanged(simulationId, tenantId, version);
      } catch (Exception e) {
        // Never let a nudge failure lose the persisted decision - the safety-net poll still
        // delivers it, just a beat later.
        log.warn("[Autonomous] Attack-path nudge failed for run {}", runId, e);
      }
    }
    return saved;
  }

  /**
   * Builds the 64-bit advisory-lock key for a run: the fixed {@link #EVENT_SEQUENCE_LOCK_NAMESPACE}
   * in the high 32 bits and the run id's hash in the low 32 bits. A hash collision across two
   * different runs merely serialises their (independent) appends for an instant - never a
   * correctness issue - while an exact per-run match is what actually closes the sequence race.
   */
  private static long sequenceLockKey(String runId) {
    return (EVENT_SEQUENCE_LOCK_NAMESPACE << 32) | (runId.hashCode() & 0xFFFF_FFFFL);
  }

  /**
   * Appends a run's END event ("Run canceled" / "Run completed" / "Run timed out" / "Run failed")
   * exactly once per run life. Any second terminal narration - a racing settle path that also won a
   * flip, or the reconcile re-settling a run that a late orchestrator write briefly resurrected -
   * is dropped instead of appended, which is the fix for the duplicated + repeated terminal message
   * the operator saw when canceling a run. Returns {@code null} when the run's end was already
   * narrated. The guard resets naturally on restart / promote, which purge the whole timeline.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousEvent appendTerminalStatusOnce(
      String runId, String tenantId, String simulationId, String title, String content) {
    // Take the per-run advisory lock BEFORE the existence check, not only inside doAppend: two
    // racing settle paths could otherwise both observe "no terminal event yet", then serialise on
    // the append lock and write two distinct terminal lines as N and N+1 - which the unique
    // (run_id, sequence) index cannot prevent. Holding the lock across check + append makes the
    // second transaction wait, then (READ COMMITTED) observe the first one's committed terminal
    // event and drop its duplicate.
    eventRepository.lockRunEventSequence(sequenceLockKey(runId));
    if (eventRepository.existsTerminalStatusEvent(runId, TERMINAL_STATUS_TITLES)) {
      return null;
    }
    return doAppend(
        runId, tenantId, simulationId, AutonomousEventType.STATUS, title, content, null);
  }

  @Transactional(readOnly = true)
  public List<AutonomousEvent> timeline(String runId) {
    return eventRepository.findByRunIdOrderBySequenceAsc(runId);
  }

  @Transactional(readOnly = true)
  public List<AutonomousEvent> timelineSince(String runId, long sinceSequence) {
    return eventRepository.findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(
        runId, sinceSequence);
  }

  /**
   * Instant of the run's newest timeline entry, or {@code null} when it has none yet. This is the
   * run's liveness clock for the idle/stall watchdog ({@link
   * AutonomousRunService#enforceLiveness}): every active decision cycle appends events (including a
   * ~45s "still working" heartbeat), so a newest-event age far past that cadence means the
   * orchestrator has gone silent.
   */
  @Transactional(readOnly = true)
  public Instant lastActivityAt(String runId) {
    return eventRepository.findMaxCreatedAt(runId);
  }

  /**
   * Takes the per-run event-sequence advisory lock for the CURRENT transaction - the same lock
   * {@link #append} / {@link #appendTerminalStatusOnce} / {@link #deleteByRun} take. The idle/stall
   * watchdog ({@link AutonomousRunService#enforceLiveness}) calls this AFTER it has row-locked the
   * run and BEFORE it reads {@link #lastActivityAt}, so the liveness read and the terminal flip are
   * serialized against a concurrent timeline append: a heartbeat committing right now is waited on
   * (the watchdog then reads the fresh activity and does NOT stall), and one that arrives later is
   * blocked until the watchdog's flip commits. Acquiring this AFTER the run ROW lock preserves the
   * platform-wide {@code row -> advisory} order (see {@link #deleteByRun}); acquiring it first
   * would invert that order and risk a deadlock against a concurrent operator Stop / reconcile.
   * Declared {@code MANDATORY} because an advisory lock in its own throwaway transaction would
   * release immediately and serialize nothing.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockRunTimeline(String runId) {
    eventRepository.lockRunEventSequence(sequenceLockKey(runId));
  }

  /**
   * Purges a run's entire decision timeline (used when the run itself is deleted, or reset by
   * restart / promote).
   *
   * <p>The purge participates in the same per-run advisory-lock protocol as the appenders: without
   * it, a purge racing an in-flight terminal append could interleave so the old life's "Run
   * canceled" row commits AFTER the reset wiped the timeline, resurrecting a stale terminal line
   * inside a freshly reset (or about-to-be-deleted) timeline. Holding the lock serialises the two:
   * an append that wins commits first and is then purged with everything else; a purge that wins
   * leaves the late appender to re-check against the run's committed state (its callers gate on a
   * row-locked status read, so a stale settle no longer narrates at all).
   *
   * <p>LOCK ORDER: every caller that also writes the {@code autonomous_runs} row takes the run ROW
   * lock BEFORE this advisory lock (restart / promote read through {@code requireForUpdate}; the
   * teardown paths re-read through the row-locking lookup) - the same {@code row -> advisory} order
   * the settle paths use (conditional row-locking UPDATE, then terminal append). Acquiring in the
   * opposite order would deadlock a purge against a concurrent settle.
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteByRun(String runId) {
    eventRepository.lockRunEventSequence(sequenceLockKey(runId));
    eventRepository.deleteByRunId(runId);
  }
}
