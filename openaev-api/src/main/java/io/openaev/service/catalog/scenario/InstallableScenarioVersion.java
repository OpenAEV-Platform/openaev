package io.openaev.service.catalog.scenario;

import io.openaev.service.catalog.InstallableVersion;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import java.util.List;
import java.util.stream.Collectors;

/** A released scenario version to install, with the payload versions it runs. */
public record InstallableScenarioVersion(
    String repository,
    String externalId,
    String version,
    CatalogScenarioType type,
    String author,
    String archiveHash,
    List<InstallablePayloadVersion> payloads)
    implements InstallableVersion {

  public ReleasedScenario released() {
    return new ReleasedScenario(
        archiveHash,
        payloads.stream()
            .collect(
                Collectors.toMap(
                    InstallablePayloadVersion::externalId, InstallablePayloadVersion::version)));
  }
}
