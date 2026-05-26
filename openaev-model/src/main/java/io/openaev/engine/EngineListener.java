package io.openaev.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;

import io.openaev.database.audit.IndexEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class EngineListener {

  static final String PENDING_DELETE_IDS_RESOURCE_KEY =
      EngineListener.class.getName() + ".PENDING_DELETE_IDS";

  private final EngineService esService;

  @EventListener
  public void listenIndexEvent(IndexEvent event) {
    if (!Objects.equals(event.getType(), DATA_DELETE) || event.getId() == null) {
      return;
    }

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      this.esService.bulkDelete(List.of(event.getId()));
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
    this.esService.bulkDelete(new ArrayList<>(pendingDeleteIds));
  }

  private void clearPendingDeletes() {
    TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_DELETE_IDS_RESOURCE_KEY);
  }
}
