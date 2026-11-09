package io.openaev.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.api.catalog.dto.CatalogManifest;
import java.util.Map;
import java.util.Optional;

/** A release whose files were checked: why versions were refused, and the payload files read. */
public record CheckedRelease(
    CatalogManifest manifest, Map<String, String> refusals, Map<String, JsonNode> payloadFiles) {

  public Optional<String> refusal(String externalId, String version) {
    return Optional.ofNullable(refusals.get(CatalogManifest.key(externalId, version)));
  }

  public JsonNode payloadFile(CatalogManifest.PayloadVersion version) {
    return payloadFiles.get(CatalogManifest.key(version.openaevId(), version.version()));
  }
}
