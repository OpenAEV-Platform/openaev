package io.openaev.service.catalog;

import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.api.catalog.dto.CatalogSyncOutput;
import io.openaev.context.TxCtx;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseRefused;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** A release sent by the collector: checked, stored, then applied to the tenant. */
// Not transactional: archives go to file storage first, then apply() writes in its own transaction.
@Service
@RequiredArgsConstructor
public class CatalogSyncService {

  static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final CatalogFileService catalogFileService;
  private final CatalogService catalogService;
  private final ApplicationEventPublisher eventPublisher;

  // -- CREATE --

  /**
   * Applies a release to the tenant's catalogue: an item failing its checks is refused alone, the
   * same release twice changes nothing, and an older release is refused.
   */
  public CatalogSyncOutput sync(
      TxCtx ctx, String tenantId, CatalogManifest manifest, Map<String, byte[]> archives) {
    try {
      validate(manifest);
      CheckedRelease checked = catalogFileService.verifyAndStore(manifest, archives);
      return catalogService.apply(ctx, tenantId, checked);
    } catch (BadRequestException e) {
      refuse(tenantId, manifest.release(), e.getMessage());
      throw e;
    }
  }

  /** Sends a Sync blocked event, once the transaction rolls back, for a refused release. */
  public void refuse(String tenantId, String release, String reason) {
    eventPublisher.publishEvent(new CatalogReleaseRefused(tenantId, release, reason));
  }

  private static void validate(CatalogManifest manifest) {
    if (!Integer.valueOf(SUPPORTED_SCHEMA_VERSION).equals(manifest.schemaVersion())) {
      throw new BadRequestException(
          "Unsupported manifest schema version: " + manifest.schemaVersion());
    }
    if (manifest.repository() == null || !SemanticVersion.isValid(manifest.release())) {
      throw new BadRequestException(
          "The manifest needs a repository and a release named as a semantic version");
    }
  }
}
