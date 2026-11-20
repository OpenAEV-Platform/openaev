package io.openaev.service.catalog.scenario.update;

import io.openaev.database.model.catalog.CatalogScenario;
import java.util.List;

/**
 * What an update or a rollback changes, the payload versions its result runs to check, and the
 * changelog of each version it brings, newest first.
 */
public record ScenarioPreview(
    ScenarioMerge.Result merge, ScenarioPayloadCheck payloads, List<CatalogScenario> notes) {}
