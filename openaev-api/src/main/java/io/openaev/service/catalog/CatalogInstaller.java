package io.openaev.service.catalog;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogHistory;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Installs released catalogue versions, each reading its files outside any transaction, then
 * installing them in one of its own.
 */
@Slf4j
public abstract class CatalogInstaller<V extends InstallableVersion, R> {

  protected abstract CatalogOffer<V> offer();

  protected abstract R install(TxCtx ctx, String tenantId, V released, CatalogHistory.Mode mode)
      throws Exception;

  /** Installs the latest version of every item the tenant never installed. */
  public Installed<V, R> installAll(TxCtx ctx, String tenantId) {
    return installEach(
        ctx, tenantId, offer().neverInstalled(ctx, tenantId), CatalogHistory.Mode.MANUAL);
  }

  /** Installs what a release brings to install by itself. */
  public Installed<V, R> autoInstall(TxCtx ctx, String tenantId) {
    return installEach(
        ctx, tenantId, offer().toAutoInstall(ctx, tenantId), CatalogHistory.Mode.AUTO);
  }

  /** Installs each version; one that fails is logged and skipped, the others still install. */
  protected Installed<V, R> installEach(
      TxCtx ctx, String tenantId, List<V> versions, CatalogHistory.Mode mode) {
    List<R> installed = new ArrayList<>();
    List<V> failed = new ArrayList<>();
    for (V released : versions) {
      try {
        installed.add(install(ctx, tenantId, released, mode));
      } catch (Exception e) {
        log.warn("Catalogue {} {} not installed", released.externalId(), released.version(), e);
        failed.add(released);
      }
    }
    return new Installed<>(installed, failed);
  }
}
