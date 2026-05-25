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

  private static final ThreadLocal<Set<String>> PENDING_DELETE_IDS =
      ThreadLocal.withInitial(LinkedHashSet::new);
  private static final ThreadLocal<Boolean> SYNC_REGISTERED = ThreadLocal.withInitial(() -> false);

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

    PENDING_DELETE_IDS.get().add(event.getId());
    if (Boolean.TRUE.equals(SYNC_REGISTERED.get())) {
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
    SYNC_REGISTERED.set(true);
  }

  private void flushPendingDeletes() {
    Set<String> pendingDeleteIds = PENDING_DELETE_IDS.get();
    if (pendingDeleteIds.isEmpty()) {
      return;
    }
    this.esService.bulkDelete(new ArrayList<>(pendingDeleteIds));
  }

  private void clearPendingDeletes() {
    PENDING_DELETE_IDS.remove();
    SYNC_REGISTERED.remove();
  }
}
