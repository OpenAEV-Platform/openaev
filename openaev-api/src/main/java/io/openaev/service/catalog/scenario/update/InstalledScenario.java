package io.openaev.service.catalog.scenario.update;

/** A scenario installed from the catalogue, and the version it runs. */
public record InstalledScenario(
    String scenarioId, String tenantId, String externalId, String version) {}
