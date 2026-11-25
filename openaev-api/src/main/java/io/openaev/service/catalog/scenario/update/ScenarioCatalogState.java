package io.openaev.service.catalog.scenario.update;

/** Whether a scenario runs a revoked catalogue version, and the update it can roll back if any. */
public record ScenarioCatalogState(boolean revoked, NumberedRevision lastUpdate) {}
