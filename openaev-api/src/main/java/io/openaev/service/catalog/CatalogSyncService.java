package io.openaev.service.catalog;

import io.openaev.api.catalog.CatalogManifest;
import io.openaev.api.catalog.CatalogSyncOutput;
import io.openaev.context.TxCtx;
import io.openaev.rest.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A release sent by the collector: checked, stored, then applied to the tenant. */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CatalogSyncService {

  static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final CatalogService catalogService;
  private final CatalogPayloadService catalogPayloadService;
  private final CatalogInstallService catalogInstallService;

  // -- CREATE --

  /**
   * Applies a release to the tenant's catalogue: an item failing its checks is refused alone, the
   * same release twice changes nothing, and an older release is refused.
   */
  public CatalogSyncOutput sync(
      TxCtx ctx, String tenantId, CatalogManifest manifest, Map<String, byte[]> archives)
      throws Exception {
    validate(manifest);
    Map<String, String> refusals = catalogService.verifyAndStore(manifest, archives);
    CatalogSyncOutput output = catalogService.apply(ctx, tenantId, manifest, archives, refusals);
    if (output.applied()) {
      catalogPayloadService.afterSync(ctx, tenantId, manifest, !output.firstSync());
      catalogInstallService.linkCopies(
          manifest.repository(), manifest.items() == null ? List.of() : manifest.items());
    }
    return output;
  }

  private static void validate(CatalogManifest manifest) {
    if (!Integer.valueOf(SUPPORTED_SCHEMA_VERSION).equals(manifest.schemaVersion())) {
      throw new BadRequestException(
          "Unsupported manifest schema version: " + manifest.schemaVersion());
    }
    if (manifest.repository() == null || !CatalogReleaseOrder.isValid(manifest.release())) {
      throw new BadRequestException(
          "The manifest needs a repository and a release named YYYY.MM.DD.N");
    }
  }
}
