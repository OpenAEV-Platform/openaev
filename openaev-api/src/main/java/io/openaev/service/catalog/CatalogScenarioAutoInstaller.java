package io.openaev.service.catalog;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CatalogItem;
import io.openaev.notification.content.ContentNotificationEvents.CatalogReleaseApplied;
import io.openaev.notification.content.ContentNotificationEvents.ScenariosInstalledAutomatically;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Installs the new catalogue scenarios of a committed release when the tenant turned it on, one
 * transaction per scenario: a scenario that fails leaves the release and the others installed.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CatalogScenarioAutoInstaller {

  private final CatalogInstallService catalogInstallService;
  private final TenantScopedTransaction tenantTx;
  private final ApplicationEventPublisher eventPublisher;

  @TransactionalEventListener
  public void onReleaseApplied(CatalogReleaseApplied event) {
    String tenantId = event.tenantId();
    TxCtx ctx = TxCtx.forTenant(tenantId);
    List<CatalogItem> installable =
        tenantTx.executeNew(
            ctx,
            () ->
                catalogInstallService.isAutoInstall(tenantId)
                    ? catalogInstallService.installAllPlan().installable()
                    : List.<CatalogItem>of());
    List<String> installed = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    for (CatalogItem item : installable) {
      try {
        tenantTx.executeNew(ctx, () -> install(ctx, tenantId, item));
        installed.add(item.getName());
      } catch (RuntimeException e) {
        log.warn(
            "Automatic install of scenario {} {} failed",
            item.getExternalId(),
            item.getVersion(),
            e);
        failed.add(item.getName());
      }
    }
    if (!event.firstSync() && (!installed.isEmpty() || !failed.isEmpty())) {
      tenantTx.executeNew(
          ctx,
          () ->
              eventPublisher.publishEvent(
                  new ScenariosInstalledAutomatically(
                      tenantId, event.release(), installed, failed)));
    }
  }

  private void install(TxCtx ctx, String tenantId, CatalogItem item) {
    try {
      catalogInstallService.install(ctx, tenantId, item.getExternalId(), item.getVersion(), null);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
