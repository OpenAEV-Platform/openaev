package io.openaev.service.catalog;

/** Whether the payloads and the scenarios a release brings install themselves. */
public record CatalogSettings(boolean payloadAutoInstall, boolean scenarioAutoInstall) {}
