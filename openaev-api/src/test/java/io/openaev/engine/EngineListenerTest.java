package io.openaev.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.openaev.database.audit.IndexEvent;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class EngineListenerTest {

  @Mock private EngineService engineService;
  @Mock private EngineDeletionJournal deletionJournal;

  @InjectMocks private EngineListener engineListener;

  @AfterEach
  void cleanupSynchronization() {
    TransactionSynchronizationManager.unbindResourceIfPossible(
        EngineListener.PENDING_DELETE_IDS_RESOURCE_KEY);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("given_deleteEventOutsideTransaction_should_journalThenDeleteImmediately")
  void given_deleteEventOutsideTransaction_should_journalThenDeleteImmediately() {
    // Arrange
    IndexEvent event = new IndexEvent(DATA_DELETE, "inject-1");

    // Act
    engineListener.listenIndexEvent(event);

    // Assert: the journal write must precede the engine flush so the replay safety net covers an
    // engine failure or an indexer resurrection.
    InOrder inOrder = inOrder(deletionJournal, engineService);
    inOrder.verify(deletionJournal).record(List.of("inject-1"));
    inOrder.verify(engineService).bulkDelete(List.of("inject-1"));
  }

  @Test
  @DisplayName("given_multipleDeleteEventsInTransaction_should_flushOnceAfterCommit")
  void given_multipleDeleteEventsInTransaction_should_flushOnceAfterCommit() {
    // Arrange
    TransactionSynchronizationManager.initSynchronization();

    // Act
    engineListener.listenIndexEvent(new IndexEvent(DATA_DELETE, "inject-1"));
    engineListener.listenIndexEvent(new IndexEvent(DATA_DELETE, "inject-2"));
    engineListener.listenIndexEvent(new IndexEvent(DATA_DELETE, "inject-1"));

    List<TransactionSynchronization> synchronizations =
        TransactionSynchronizationManager.getSynchronizations();
    synchronizations.forEach(TransactionSynchronization::afterCommit);
    synchronizations.forEach(
        synchronization ->
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    TransactionSynchronizationManager.clearSynchronization();

    // Assert
    ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
    verify(engineService).bulkDelete(idsCaptor.capture());
    assertThat(idsCaptor.getValue()).containsExactly("inject-1", "inject-2");
    ArgumentCaptor<Collection<String>> journaledCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(deletionJournal).record(journaledCaptor.capture());
    assertThat(journaledCaptor.getValue()).containsExactly("inject-1", "inject-2");
  }

  @Test
  @DisplayName("given_engineFailureOutsideTransaction_should_notPropagateToCaller")
  void given_engineFailureOutsideTransaction_should_notPropagateToCaller() {
    // Arrange: the engine rejects the flush (e.g. search_phase_execution_exception)
    doThrow(new RuntimeException("engine down")).when(engineService).bulkDelete(anyList());

    // Act & Assert: the deletion is journaled and the failure is swallowed - the replay job
    // converges the engine later.
    assertThatCode(() -> engineListener.listenIndexEvent(new IndexEvent(DATA_DELETE, "inject-1")))
        .doesNotThrowAnyException();
    verify(deletionJournal).record(List.of("inject-1"));
  }

  @Test
  @DisplayName("given_engineFailureAfterCommit_should_notPropagateToCommittingTransaction")
  void given_engineFailureAfterCommit_should_notPropagateToCommittingTransaction() {
    // Arrange: the DB transaction commits, then the engine rejects the after-commit flush. This
    // must never fail the caller: the rows are already deleted from PostgreSQL (bulk scenario
    // deletions were observed returning HTTP 500 for already-committed chunks).
    TransactionSynchronizationManager.initSynchronization();
    doThrow(new RuntimeException("engine down")).when(engineService).bulkDelete(anyList());

    engineListener.listenIndexEvent(new IndexEvent(DATA_DELETE, "inject-1"));
    List<TransactionSynchronization> synchronizations =
        TransactionSynchronizationManager.getSynchronizations();

    // Act & Assert
    assertThatCode(() -> synchronizations.forEach(TransactionSynchronization::afterCommit))
        .doesNotThrowAnyException();
    verify(deletionJournal).record(any());
    TransactionSynchronizationManager.clearSynchronization();
  }

  @Test
  @DisplayName("given_deleteEventsInTransactionRollback_should_notFlush")
  void given_deleteEventsInTransactionRollback_should_notFlush() {
    // Arrange
    TransactionSynchronizationManager.initSynchronization();

    // Act
    engineListener.listenIndexEvent(new IndexEvent(DATA_DELETE, "inject-1"));

    List<TransactionSynchronization> synchronizations =
        TransactionSynchronizationManager.getSynchronizations();
    synchronizations.forEach(
        synchronization ->
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    TransactionSynchronizationManager.clearSynchronization();

    // Assert
    verify(engineService, never()).bulkDelete(List.of("inject-1"));
    verify(deletionJournal, never()).record(any());
  }

  @Test
  @DisplayName("given_deleteEventInTransaction_should_bindPendingIdsAsTransactionResource")
  void given_deleteEventInTransaction_should_bindPendingIdsAsTransactionResource() {
    // Arrange
    TransactionSynchronizationManager.initSynchronization();

    // Act
    engineListener.listenIndexEvent(new IndexEvent(DATA_DELETE, "inject-1"));

    // Assert
    assertThat(
            TransactionSynchronizationManager.hasResource(
                EngineListener.PENDING_DELETE_IDS_RESOURCE_KEY))
        .isTrue();
  }

  @Test
  @DisplayName("given_nonDeleteEvent_should_ignore")
  void given_nonDeleteEvent_should_ignore() {
    // Arrange
    IndexEvent event = new IndexEvent("DATA_UPDATE", "inject-1");

    // Act
    engineListener.listenIndexEvent(event);

    // Assert
    verify(engineService, never()).bulkDelete(List.of("inject-1"));
  }
}
