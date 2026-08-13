package io.openaev.service.autonomous;

import io.openaev.context.TenantContext;
import io.openaev.database.model.autonomous.AutonomousEvent;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
      Set.of("Run canceled", "Run completed", "Run timed out", "Run failed");

  /**
   * High 32 bits of the per-run advisory-lock key, namespacing autonomous event-sequence locks so
   * they can never collide with any other advisory lock the platform might take. The value is
   * arbitrary but stable ("AE" = autonomous event).
   */
  private static final long EVENT_SEQUENCE_LOCK_NAMESPACE = 0x4145_0001L;

  @Transactional(rollbackFor = Exception.class)
  public AutonomousEvent append(
      String runId,
      String simulationId,
      AutonomousEventType type,
      String title,
      String content,
      String data) {
    return doAppend(runId, simulationId, type, title, content, data);
  }

  /**
   * Body of {@link #append}. Kept un-annotated so {@link #appendTerminalStatusOnce} can reuse it
   * without the intra-class {@code @Transactional} self-invocation trap (a same-class call bypasses
   * the Spring proxy). Must be called inside an active transaction.
   */
  private AutonomousEvent doAppend(
      String runId,
      String simulationId,
      AutonomousEventType type,
      String title,
      String content,
      String data) {
    // Serialise concurrent appenders to THIS run before the read-max-then-insert below: two
    // decision
    // cycles (or a cycle racing an operator directive) writing to the same run must not both
    // compute
    // the same next sequence. The transaction advisory lock makes both succeed as N and N+1; the
    // UNIQUE (run_id, sequence) index is the backstop that makes a duplicate impossible regardless.
    eventRepository.lockRunEventSequence(sequenceLockKey(runId));
    AutonomousEvent event = new AutonomousEvent();
    event.setRunId(runId);
    event.setSequence(eventRepository.findMaxSequence(runId) + 1);
    event.setType(type);
    event.setTitle(title);
    event.setContent(content);
    event.setData(data);
    AutonomousEvent saved = eventRepository.save(event);

    // Nudge the attack-path SSE so the live view refreshes graph + timeline together.
    if (simulationId != null && !simulationId.isBlank()) {
      try {
        String tenantId = TenantContext.getCurrentTenant();
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
      String runId, String simulationId, String title, String content) {
    if (eventRepository.existsTerminalStatusEvent(runId, TERMINAL_STATUS_TITLES)) {
      return null;
    }
    return doAppend(runId, simulationId, AutonomousEventType.STATUS, title, content, null);
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

  /** Purges a run's entire decision timeline (used when the run itself is deleted). */
  @Transactional(rollbackFor = Exception.class)
  public void deleteByRun(String runId) {
    eventRepository.deleteByRunId(runId);
  }
}
