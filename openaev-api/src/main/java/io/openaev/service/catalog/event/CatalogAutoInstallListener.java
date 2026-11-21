package io.openaev.service.catalog.event;

import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.notification.catalog.CatalogEvents.InstalledAutomatically;
import io.openaev.service.catalog.InstallableVersion;
import io.openaev.service.catalog.Installed;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import io.openaev.service.catalog.scenario.CatalogScenarioInstaller;
import io.openaev.service.catalog.scenario.InstallableScenarioVersion;
import java.util.stream.Stream;
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
    Installed<InstallablePayloadVersion, Payload> payloads =
        ambientTenantBridge.callInTenant(
            tenantId, () -> catalogPayloadInstaller.autoInstall(ctx, tenantId));
    Installed<InstallableScenarioVersion, Scenario> scenarios =
        ambientTenantBridge.callInTenant(
            tenantId, () -> catalogScenarioInstaller.autoInstall(ctx, tenantId));
    InstalledAutomatically installed =
        new InstalledAutomatically(
            tenantId,
            event.release(),
            payloads.installed().stream().map(Payload::getId).toList(),
            scenarios.installed().stream().map(Scenario::getName).toList(),
            Stream.<InstallableVersion>concat(
                    payloads.failed().stream(), scenarios.failed().stream())
                .map(failed -> failed.externalId() + " v" + failed.version())
                .toList());
    if (!installed.isEmpty()) {
      eventPublisher.publishEvent(installed);
    }
  }
}
