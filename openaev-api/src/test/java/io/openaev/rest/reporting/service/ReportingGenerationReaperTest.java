package io.openaev.rest.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.repository.ReportingGenerationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Reporting generation reaper")
class ReportingGenerationReaperTest {

  private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");
  private static final long RENDER_TIMEOUT_SECONDS = 90;

  /** MAX_PAGE_ATTEMPTS full page loads of 90s, plus the fixed minute of slack. */
  private static final Instant CUTOFF = NOW.minusSeconds(90 * 3 + 60);

  private ReportingGenerationRepository generationRepository;
  private Session session;
  private ReportingGenerationReaper reaper;

  @BeforeEach
  void setUp() {
    generationRepository = mock(ReportingGenerationRepository.class);
    session = mock(Session.class);
    EntityManager entityManager = mock(EntityManager.class);
    when(entityManager.unwrap(Session.class)).thenReturn(session);
    reaper =
        new ReportingGenerationReaper(generationRepository, entityManager, RENDER_TIMEOUT_SECONDS);
  }

  private ReportingGeneration generation(ReportingGenerationStatus status, Instant createdAt) {
    ReportingGeneration generation = new ReportingGeneration();
    generation.setId("generation-1");
    generation.setStatus(status);
    generation.setCreatedAt(createdAt);
    return generation;
  }

  @Test
  @DisplayName("fails a generation left running past the render budget")
  void failsStuckGeneration() {
    ReportingGeneration stuck =
        generation(ReportingGenerationStatus.RUNNING, CUTOFF.minusSeconds(1));
    when(generationRepository.findAllByStatusInAndCreatedAtBefore(any(), any()))
        .thenReturn(List.of(stuck));

    int failed = reaper.failStuckGenerations(NOW);

    assertThat(failed).isEqualTo(1);
    assertThat(stuck.getStatus()).isEqualTo(ReportingGenerationStatus.ERROR);
    assertThat(stuck.getErrorMessage()).contains("never completed this generation");
    assertThat(stuck.getCompletedAt()).isEqualTo(NOW);
    verify(generationRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("sweeps every tenant, both transient statuses, older than the render budget")
  void sweepsTransientStatusesCrossTenant() {
    when(generationRepository.findAllByStatusInAndCreatedAtBefore(any(), any()))
        .thenReturn(List.of());

    reaper.failStuckGenerations(NOW);

    // The job runs outside any tenant context: the sweep must see every tenant's rows.
    verify(session).disableFilter("tenantFilter");
    ArgumentCaptor<Collection<ReportingGenerationStatus>> statuses =
        ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(generationRepository)
        .findAllByStatusInAndCreatedAtBefore(statuses.capture(), cutoff.capture());
    assertThat(statuses.getValue())
        .containsExactlyInAnyOrder(
            ReportingGenerationStatus.PENDING, ReportingGenerationStatus.RUNNING);
    assertThat(cutoff.getValue()).isEqualTo(CUTOFF);
  }

  @Test
  @DisplayName("writes nothing when no generation is stuck")
  void writesNothingWhenNothingStuck() {
    when(generationRepository.findAllByStatusInAndCreatedAtBefore(any(), any()))
        .thenReturn(List.of());

    assertThat(reaper.failStuckGenerations(NOW)).isZero();

    verify(generationRepository, never()).saveAll(anyList());
  }
}
