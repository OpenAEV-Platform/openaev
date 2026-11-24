package io.openaev.notification.content;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.notification.engine.NotificationEngineService;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads what a content change tells a tenant, then hands each notification to the engine. */
@Component
@RequiredArgsConstructor
public class ContentNotifier {

  private final NotificationEngineService notificationEngineService;
  private final TenantScopedTransaction tenantTx;

  // Handed over once the read transaction is closed: the engine writes in its own.
  public void send(String tenantId, Supplier<List<ContentNotification>> read) {
    tenantTx
        .executeReadOnly(TxCtx.forTenant(tenantId), read)
        .forEach(
            notification ->
                notificationEngineService.handleEventWithMessage(
                    notification.entry(),
                    notification.entityId(),
                    tenantId,
                    notification.eventType(),
                    notification.message()));
  }
}
