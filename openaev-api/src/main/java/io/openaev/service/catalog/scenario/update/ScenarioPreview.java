package io.openaev.service.catalog.scenario.update;

/** What an update or a rollback changes, and the payload versions its result runs to check. */
public record ScenarioPreview(ScenarioMerge.Result merge, ScenarioPayloadCheck payloads) {}
