package io.openaev.service.autonomous;

import io.openaev.context.TenantContext;
import io.openaev.database.model.autonomous.AutonomousEvent;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import java.util.List;
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

  @Transactional(rollbackFor = Exception.class)
  public AutonomousEvent append(
      String runId,
      String simulationId,
      AutonomousEventType type,
      String title,
      String content,
      String data) {
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
