package io.openaev.service.catalog.scenario;

import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogScenario;
import java.util.List;

/**
 * The latest version of a catalogue scenario, with what this platform lacks to install it and the
 * catalogue payloads its release names.
 */
public record OfferedScenario(
    CatalogScenario version,
    List<MissingRequirement> missingRequirements,
    List<CatalogPayload> payloads) {}
