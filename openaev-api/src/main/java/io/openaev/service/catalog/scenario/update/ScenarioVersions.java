package io.openaev.service.catalog.scenario.update;

import io.openaev.database.model.catalog.CatalogScenario;
import java.util.List;

/**
 * Every catalogue version of an installed scenario, newest first, with the installed one, the
 * newest one still offered, and the simulations the scenario ran.
 */
public record ScenarioVersions(
    List<CatalogScenario> versions,
    String installedVersion,
    String latestVersion,
    long simulationsCount) {}
