package io.openaev.service.catalog.event;

import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TxCtx;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.service.catalog.CatalogPayloadInstaller;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Installs what a release brings to install by itself, once the release is committed. */
@Component
@RequiredArgsConstructor
public class CatalogAutoInstallListener {

  private final CatalogPayloadInstaller catalogPayloadInstaller;
  private final AmbientTenantBridge ambientTenantBridge;

  // Off the request thread and after its transaction: each install opens its own, scoped by the
  // TxCtx; the bridge keeps the tables not tenant-active yet (injector contracts) in the tenant.
  @Async
  @TransactionalEventListener
  public void onReleaseApplied(CatalogReleaseApplied event) {
    String tenantId = event.tenantId();
    ambientTenantBridge.callInTenant(
        tenantId, () -> catalogPayloadInstaller.autoInstall(TxCtx.forTenant(tenantId), tenantId));
  }
}
