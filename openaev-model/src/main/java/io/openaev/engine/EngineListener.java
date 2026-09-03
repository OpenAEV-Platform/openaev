package io.openaev.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;

import io.openaev.database.audit.IndexEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.openaev.engine.facade.EngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class EngineListener {

  static final String PENDING_DELETE_IDS_RESOURCE_KEY =
      EngineListener.class.getName() + ".PENDING_DELETE_IDS";

  private final EngineService esService;
  private final EngineDeletionJournal deletionJournal;

  @EventListener
  public void listenIndexEvent(IndexEvent event) {
    if (!Objects.equals(event.getType(), DATA_DELETE) || event.getId() == null) {
      return;
    }

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      // Journal first: if the engine call fails (or an in-flight indexer batch resurrects the
      // document right after it), the periodic replay converges the engine with PostgreSQL.
      this.deletionJournal.record(List.of(event.getId()));
      flushToEngine(List.of(event.getId()));
      return;
    }

    Set<String> pendingDeleteIds = getOrCreatePendingDeleteIds();
    pendingDeleteIds.add(event.getId());
    if (pendingDeleteIds.size() > 1) {
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            flushPendingDeletes();
          }

          @Override
          public void afterCompletion(int status) {
            clearPendingDeletes();
          }
        });
  }

  private Set<String> getOrCreatePendingDeleteIds() {
    if (TransactionSynchronizationManager.hasResource(PENDING_DELETE_IDS_RESOURCE_KEY)) {
      return getBoundPendingDeleteIds();
    }
    Set<String> pendingDeleteIds = new LinkedHashSet<>();
    TransactionSynchronizationManager.bindResource(
        PENDING_DELETE_IDS_RESOURCE_KEY, pendingDeleteIds);
    return pendingDeleteIds;
  }

  @SuppressWarnings("unchecked")
  private Set<String> getBoundPendingDeleteIds() {
    return (Set<String>)
        TransactionSynchronizationManager.getResource(PENDING_DELETE_IDS_RESOURCE_KEY);
  }

  private void flushPendingDeletes() {
    if (!TransactionSynchronizationManager.hasResource(PENDING_DELETE_IDS_RESOURCE_KEY)) {
      return;
    }
    Set<String> pendingDeleteIds = getBoundPendingDeleteIds();
    if (pendingDeleteIds.isEmpty()) {
      return;
    }
    // Journal first (own short auto-commit statements: the deleting transaction has already
    // committed at this point), then flush to the engine. The journal is the durable record the
    // replay job uses to re-delete documents resurrected by an in-flight indexer batch or lost to
    // an engine outage.
    this.deletionJournal.record(pendingDeleteIds);
    flushToEngine(new ArrayList<>(pendingDeleteIds));
  }

  /**
   * Best-effort immediate engine flush. Never propagates: the deleting transaction has already
   * committed when this runs, so an engine failure must not turn an already-committed deletion into
   * a caller-facing error (bulk scenario deletions were observed failing the whole HTTP request -
   * and its massive-operation tracking - on a transient engine rejection). The ids are journaled
   * before this call; the periodic replay job converges the engine with PostgreSQL.
   */
  private void flushToEngine(List<String> ids) {
    try {
      this.esService.bulkDelete(ids);
    } catch (RuntimeException e) {
      log.error(
          "Immediate engine delete flush failed for {} id(s); journaled for replay: {}",
          ids.size(),
          e.getMessage(),
          e);
    }
  }

  private void clearPendingDeletes() {
    TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_DELETE_IDS_RESOURCE_KEY);
  }
}
