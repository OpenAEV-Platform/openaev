package io.openaev.service.catalog.scenario.targeting;

import java.util.List;

/** The targets an admin chooses for a catalogue scenario, by ID, and hosts typed by hand. */
public record TargetChoice(
    List<String> assetIds, List<String> assetGroupIds, List<String> teamIds, List<String> hosts) {

  /** No target: an install with no choice, by hand or by itself. */
  public static final TargetChoice NONE =
      new TargetChoice(List.of(), List.of(), List.of(), List.of());
}
