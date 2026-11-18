package io.openaev.service.catalog.event;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.service.payload.PayloadRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Follows the payload versions a release revokes, once the release is committed. */
@Component
@RequiredArgsConstructor
public class CatalogRevocationListener {

  private final PayloadRevocationService payloadRevocationService;
  private final TenantScopedTransaction tenantScopedTransaction;

  @Async
  @TransactionalEventListener
  public void onReleaseApplied(CatalogReleaseApplied event) {
    String tenantId = event.tenantId();
    TxCtx ctx = TxCtx.forTenant(tenantId);
    tenantScopedTransaction.execute(
        ctx, () -> payloadRevocationService.applyCatalogueRevocations(ctx, tenantId));
  }
}
