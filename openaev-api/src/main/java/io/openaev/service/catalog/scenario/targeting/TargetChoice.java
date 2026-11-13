package io.openaev.service.catalog.scenario.targeting;

import java.util.List;

/** The targets an admin chooses for a catalogue scenario, by ID, and hosts typed by hand. */
public record TargetChoice(
    List<String> assetIds, List<String> assetGroupIds, List<String> teamIds, List<String> hosts) {}
