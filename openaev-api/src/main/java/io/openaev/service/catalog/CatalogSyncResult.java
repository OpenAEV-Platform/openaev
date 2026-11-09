package io.openaev.service.catalog;

import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.api.catalog.dto.CatalogSyncOutput;
import io.openaev.api.catalog.dto.CatalogSyncOutput.Refusal;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.notification.catalog.CatalogEvents.RefusedVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What a release changed in a tenant's catalogue. */
final class CatalogSyncResult {

  private final CatalogManifest manifest;
  private final List<Refusal> refused = new ArrayList<>();
  private int added;
  private int unchanged;

  CatalogSyncResult(CatalogManifest manifest) {
    this.manifest = manifest;
  }

  void added() {
    added++;
  }

  void unchanged() {
    unchanged++;
  }

  void refused(String externalId, String version, String reason) {
    refused.add(new Refusal(externalId, version, reason));
  }

  CatalogReleaseApplied toEvent(String tenantId) {
    Map<String, String> names = manifest.names();
    return new CatalogReleaseApplied(
        tenantId,
        manifest.release(),
        refused.stream()
            .map(
                r ->
                    new RefusedVersion(
                        names.getOrDefault(r.externalId(), r.externalId()),
                        r.version(),
                        r.reason()))
            .toList());
  }

  CatalogSyncOutput toOutput() {
    return new CatalogSyncOutput(
        manifest.release(), manifest.releaseApprover(), true, added, unchanged, refused);
  }
}
