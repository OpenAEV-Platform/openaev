package io.openaev.service.catalog.scenario;

import java.util.Map;

/** The package of a released scenario version, and the version of each payload it runs. */
public record ReleasedScenario(String archiveHash, Map<String, String> payloadVersions) {}
