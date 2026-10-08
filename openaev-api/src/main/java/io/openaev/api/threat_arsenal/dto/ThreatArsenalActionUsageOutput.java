package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.raw.RawPayloadUsageItem;
import io.openaev.service.payload_approval.PayloadUsage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Where the payload of an action is used, to warn before an approval change blocks launches. The
 * name lists are null when the user cannot read that type of resource (counts only).
 */
public record ThreatArsenalActionUsageOutput(
    @Schema(description = "Number of atomic testings using the payload")
        @JsonProperty("usage_atomic_testings_count")
        long atomicTestingsCount,
    @Schema(description = "Number of scenarios using the payload")
        @JsonProperty("usage_scenarios_count")
        long scenariosCount,
    @Schema(description = "Number of simulations still to run (scheduled, running, paused)")
        @JsonProperty("usage_simulations_count")
        long simulationsCount,
    @Schema(
            description =
                "First 20 atomic testings by name (the count is exact), null without access to atomic testings")
        @JsonProperty("usage_atomic_testings")
        List<ThreatArsenalActionUsageItem> atomicTestings,
    @Schema(
            description =
                "First 20 scenarios by name (the count is exact), null without access to scenarios")
        @JsonProperty("usage_scenarios")
        List<ThreatArsenalActionUsageItem> scenarios,
    @Schema(
            description =
                "First 20 simulations by name (the count is exact), null without access to simulations")
        @JsonProperty("usage_simulations")
        List<ThreatArsenalActionUsageItem> simulations) {

  public static ThreatArsenalActionUsageOutput from(PayloadUsage usage) {
    return new ThreatArsenalActionUsageOutput(
        usage.atomicTestingsCount(),
        usage.scenariosCount(),
        usage.simulationsCount(),
        items(usage.atomicTestings()),
        items(usage.scenarios()),
        items(usage.simulations()));
  }

  private static List<ThreatArsenalActionUsageItem> items(List<RawPayloadUsageItem> raw) {
    return raw == null
        ? null
        : raw.stream().map(r -> new ThreatArsenalActionUsageItem(r.getId(), r.getName())).toList();
  }
}
