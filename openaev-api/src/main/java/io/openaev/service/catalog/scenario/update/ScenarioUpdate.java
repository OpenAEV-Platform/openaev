package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.scenario.ScenarioRevision;

/**
 * Content applied to an installed scenario, a release or a revision, with the base its installed
 * version brought and the version it leaves the scenario at.
 */
public record ScenarioUpdate(
    InstalledScenario installed,
    ObjectNode base,
    ObjectNode applied,
    String version,
    ScenarioRevision.Reason reason) {}
