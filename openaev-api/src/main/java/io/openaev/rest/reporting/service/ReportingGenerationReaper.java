package io.openaev.rest.reporting.service;

import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.repository.ReportingGenerationRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fails the generations that no render will ever complete.
 *
 * <p>A generation is moved out of PENDING/RUNNING by the render thread itself, so anything that
 * kills that thread without letting it write a terminal status - a JVM restart, a render wedged in
 * the browser driver, a dispatch that never reached the executor - leaves the row transient
 * forever. The frontend then polls a status that will never change, with no error to show and no
 * document to download.
 *
 * <p>This sweep is the backstop: past the longest render the engine can legitimately take, a
 * transient generation is declared failed with an explicit message, so the UI (and the schedule
 * engine) always reach a terminal state. A render that somehow completes afterwards overwrites the
 * row with its own outcome, which is the desired resolution.
 */
@Slf4j
@Service
public class ReportingGenerationReaper {

  private static final Set<ReportingGenerationStatus> TRANSIENT_STATUSES =
      Set.of(ReportingGenerationStatus.PENDING, ReportingGenerationStatus.RUNNING);

  static final String STUCK_ERROR_MESSAGE =
      "The rendering engine never completed this generation (no result after %d minutes). "
          + "Check that the rendering engine is available, then generate the report again.";

  private final ReportingGenerationRepository reportingGenerationRepository;
  private final EntityManager entityManager;
  private final Duration stuckAfter;

  public ReportingGenerationReaper(
      final ReportingGenerationRepository reportingGenerationRepository,
      final EntityManager entityManager,
      @Value("${openaev.reporting.render-timeout-seconds:90}") final long renderTimeoutSeconds) {
    this.reportingGenerationRepository = reportingGenerationRepository;
    this.entityManager = entityManager;
    // A render is allowed one full page load per attempt, each bounded by the render timeout; the
    // extra minute absorbs the wait for a render slot, the browser launch and the file storage.
    this.stuckAfter =
        Duration.ofSeconds(
            Math.max(1, renderTimeoutSeconds) * PlaywrightReportingRenderer.MAX_PAGE_ATTEMPTS + 60);
  }

  /**
   * Fails every generation still transient since longer than the render budget.
   *
   * @param now the evaluation instant
   * @return the number of generations failed
   */
  @Transactional
  public int failStuckGenerations(final Instant now) {
    // Generations of every tenant are swept at once: the job runs outside any tenant context.
    this.entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    Instant cutoff = now.minus(this.stuckAfter);
    List<ReportingGeneration> stuck =
        this.reportingGenerationRepository.findAllByStatusInAndCreatedAtBefore(
            TRANSIENT_STATUSES, cutoff);
    if (stuck.isEmpty()) {
      return 0;
    }
    String message = STUCK_ERROR_MESSAGE.formatted(this.stuckAfter.toMinutes());
    for (ReportingGeneration generation : stuck) {
      log.warn(
          "Reporting generation {} still {} since {}, failing it",
          generation.getId(),
          generation.getStatus(),
          generation.getCreatedAt());
      generation.setStatus(ReportingGenerationStatus.ERROR);
      generation.setErrorMessage(message);
      generation.setCompletedAt(now);
    }
    this.reportingGenerationRepository.saveAll(stuck);
    return stuck.size();
  }
}
