package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.autonomous.AutonomousEvent;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the per-run advisory-lock protocol of {@link AutonomousEventService}: every write
 * to a run's timeline - append, terminal-once append, and purge - must serialise on the SAME
 * per-run lock, each acquiring it BEFORE its first read of shared timeline state. These orderings
 * are what close the duplicate-sequence race (two appenders reading the same max), the duplicate
 * terminal-narration race (two settle paths both observing "no terminal event yet"), and the
 * purge/append race (an in-flight terminal append committing an old-life row into a freshly reset
 * timeline).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutonomousEventService advisory-lock protocol")
class AutonomousEventServiceTest {

  @Mock private AutonomousEventRepository eventRepository;
  @Mock private AttackPathVersionService attackPathVersionService;
  @InjectMocks private AutonomousEventService eventService;

  @Test
  @DisplayName("An append takes the per-run lock BEFORE computing the next sequence")
  void given_append_when_writing_then_lockPrecedesMaxSequenceRead() {
    when(eventRepository.findMaxSequence("run-1")).thenReturn(4L);
    when(eventRepository.save(any(AutonomousEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AutonomousEvent saved =
        eventService.append(
            "run-1", "tenant-1", null, AutonomousEventType.STATUS, "title", "content", null);

    assertThat(saved.getSequence()).isEqualTo(5L);
    InOrder inOrder = inOrder(eventRepository);
    inOrder.verify(eventRepository).lockRunEventSequence(anyLong());
    inOrder.verify(eventRepository).findMaxSequence("run-1");
    inOrder.verify(eventRepository).save(any(AutonomousEvent.class));
  }

  @Test
  @DisplayName("The terminal-once guard locks BEFORE its existence check and drops the duplicate")
  void given_terminalAlreadyNarrated_when_appendTerminalStatusOnce_then_lockedCheckDrops() {
    when(eventRepository.existsTerminalStatusEvent(
            "run-1", AutonomousEventService.TERMINAL_STATUS_TITLES))
        .thenReturn(true);

    AutonomousEvent result =
        eventService.appendTerminalStatusOnce(
            "run-1", "tenant-1", "sim-1", "Run canceled", "content");

    assertThat(result).isNull();
    InOrder inOrder = inOrder(eventRepository);
    inOrder.verify(eventRepository).lockRunEventSequence(anyLong());
    inOrder
        .verify(eventRepository)
        .existsTerminalStatusEvent("run-1", AutonomousEventService.TERMINAL_STATUS_TITLES);
    verify(eventRepository, never()).save(any());
  }

  @Test
  @DisplayName("A timeline purge takes the per-run lock BEFORE deleting (reset vs append race)")
  void given_deleteByRun_when_purging_then_lockPrecedesDelete() {
    eventService.deleteByRun("run-1");

    InOrder inOrder = inOrder(eventRepository);
    inOrder.verify(eventRepository).lockRunEventSequence(anyLong());
    inOrder.verify(eventRepository).deleteByRunId("run-1");
  }
}
