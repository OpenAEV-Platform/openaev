package io.openaev.service.catalog.scenario;

import io.openaev.service.catalog.InstallableVersion;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import java.util.List;

/** A released scenario version to install, with the payload versions it runs. */
public record InstallableScenarioVersion(
    String repository,
    String externalId,
    String version,
    String author,
    String archiveHash,
    List<InstallablePayloadVersion> payloads)
    implements InstallableVersion {}
