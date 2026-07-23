package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.engine.EngineDeletionJournal;
import io.openaev.engine.EngineService;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionException;

@ExtendWith(MockitoExtension.class)
class EngineDeletionReplayJobTest {

  @Mock private EngineDeletionJournal deletionJournal;
  @Mock private EngineService engineService;

  @InjectMocks private EngineDeletionReplayJob job;

  @Test
  @DisplayName("Given pending journaled deletions, should replay them then prune the journal")
  void given_pendingDeletions_should_replayThenPrune() throws JobExecutionException {
    when(deletionJournal.findPendingIds()).thenReturn(List.of("id-1", "id-2"));

    job.execute(null);

    // Prune must run after the replay so entries older than the retention window have been
    // replayed at least once, even after a long platform downtime.
    InOrder inOrder = inOrder(engineService, deletionJournal);
    inOrder.verify(engineService).bulkDelete(List.of("id-1", "id-2"));
    inOrder.verify(deletionJournal).prune();
  }

  @Test
  @DisplayName("Given more pending deletions than the batch size, should replay in batches")
  void given_moreDeletionsThanBatchSize_should_replayInBatches() throws JobExecutionException {
    List<String> ids =
        IntStream.range(0, EngineDeletionReplayJob.REPLAY_BATCH_SIZE + 10)
            .mapToObj(i -> "id-" + i)
            .toList();
    when(deletionJournal.findPendingIds()).thenReturn(ids);

    job.execute(null);

    ArgumentCaptor<List<String>> batchCaptor = ArgumentCaptor.forClass(List.class);
    verify(engineService, times(2)).bulkDelete(batchCaptor.capture());
    assertThat(batchCaptor.getAllValues().get(0))
        .hasSize(EngineDeletionReplayJob.REPLAY_BATCH_SIZE);
    assertThat(batchCaptor.getAllValues().get(1)).hasSize(10);
    assertThat(batchCaptor.getAllValues().stream().flatMap(List::stream).toList()).isEqualTo(ids);
  }

  @Test
  @DisplayName("Given an empty journal, should not call the engine but still prune")
  void given_emptyJournal_should_notCallEngineButStillPrune() throws JobExecutionException {
    when(deletionJournal.findPendingIds()).thenReturn(List.of());

    job.execute(null);

    verify(engineService, never()).bulkDelete(anyList());
    verify(deletionJournal).prune();
  }
}
