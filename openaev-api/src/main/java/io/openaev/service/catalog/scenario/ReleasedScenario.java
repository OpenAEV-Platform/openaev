package io.openaev.service.catalog.scenario;

import static java.util.stream.Collectors.toMap;

import io.openaev.api.catalog.dto.CatalogManifest.PayloadRef;
import java.util.List;
import java.util.Map;

/** The package of a released scenario version, and the payload versions its release names. */
public record ReleasedScenario(String archiveHash, List<PayloadRef> payloads) {

  /** The version of each payload, by external ID. */
  public Map<String, String> payloadVersions() {
    return payloads.stream().collect(toMap(PayloadRef::externalId, PayloadRef::version));
  }
}
