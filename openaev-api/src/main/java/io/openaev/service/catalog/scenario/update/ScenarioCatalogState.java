package io.openaev.service.catalog.scenario.update;

/** A newer version is one the scenario can move to: chained ones cannot yet. */
public record ScenarioCatalogState(
    boolean revoked, String newerVersion, NumberedRevision lastUpdate) {}
