package io.openaev.rest.reporting.service;

import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.User;
import io.openaev.database.repository.ReportingGenerationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fallback renderer: every requested generation immediately fails with an explicit error message.
 * The real engine is {@link PlaywrightReportingRenderer} (marked @Primary); this bean only handles
 * injection points that explicitly ask for it.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnMissingBean(value = ReportingRenderer.class, ignored = NoopReportingRenderer.class)
public class NoopReportingRenderer implements ReportingRenderer {

  private final ReportingGenerationRepository reportingGenerationRepository;

  @Override
  @Transactional
  public void render(ReportingGeneration generation, User actingUser) {
    generation.setStatus(ReportingGenerationStatus.ERROR);
    generation.setErrorMessage("Rendering engine not available");
    generation.setCompletedAt(Instant.now());
    this.reportingGenerationRepository.save(generation);
  }
}
