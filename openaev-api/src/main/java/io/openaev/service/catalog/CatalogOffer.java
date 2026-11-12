package io.openaev.service.catalog;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogIdentity;
import java.util.List;

/** The released versions of one kind of catalogue content a tenant can install. */
public interface CatalogOffer<V extends InstallableVersion> {

  /** The latest version of every item of this kind the tenant never installed. */
  List<V> neverInstalled(TxCtx ctx, String tenantId);

  /** What a release brings to install by itself. */
  List<V> toAutoInstall(TxCtx ctx, String tenantId);

  /** One catalogue item across its versions: its repository and its external ID. */
  static String key(String repository, String externalId) {
    return repository + "/" + externalId;
  }

  static String key(CatalogIdentity identity) {
    return key(identity.getRepository(), identity.getExternalId());
  }
}
