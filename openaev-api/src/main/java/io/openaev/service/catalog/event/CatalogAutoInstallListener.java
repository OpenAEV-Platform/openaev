package io.openaev.service.catalog.event;

import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.notification.catalog.CatalogEvents.ScenariosInstalledAutomatically;
import io.openaev.service.catalog.Installed;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.openaev.service.catalog.scenario.CatalogScenarioInstaller;
import io.openaev.service.catalog.scenario.InstallableScenarioVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Installs what a release brings to install by itself, once the release is committed. */
@Component
@RequiredArgsConstructor
public class CatalogAutoInstallListener {

  private final CatalogPayloadInstaller catalogPayloadInstaller;
  private final CatalogScenarioInstaller catalogScenarioInstaller;
  private final AmbientTenantBridge ambientTenantBridge;
  private final ApplicationEventPublisher eventPublisher;

  // Off the request thread and after its transaction: each install opens its own, scoped by the
  // TxCtx; the bridge keeps the tables not tenant-active yet (injector contracts) in the tenant.
  // Payloads first: a scenario then finds the payload versions it runs already installed.
  @Async
  @TransactionalEventListener
  public void onReleaseApplied(CatalogReleaseApplied event) {
    String tenantId = event.tenantId();
    TxCtx ctx = TxCtx.forTenant(tenantId);
    Installed<InstallableScenarioVersion, Scenario> scenarios =
        ambientTenantBridge.callInTenant(
            tenantId,
            () -> {
              catalogPayloadInstaller.autoInstall(ctx, tenantId);
              return catalogScenarioInstaller.autoInstall(ctx, tenantId);
            });
    if (!scenarios.installed().isEmpty() || !scenarios.failed().isEmpty()) {
      eventPublisher.publishEvent(
          new ScenariosInstalledAutomatically(
              tenantId,
              event.release(),
              scenarios.installed().stream().map(Scenario::getName).toList(),
              scenarios.failed().stream()
                  .map(failed -> failed.externalId() + " v" + failed.version())
                  .toList()));
    }
  }
}
