package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Asset freshness in this tenant: an asset unseen for this many days turns stale. 0 by default (the
 * check is off and no asset changes state); a user opts in by setting a number of days.
 *
 * @param staleAfterDays days without an observation before an asset is stale (0: never)
 * @param discoveryTag tag the assets a run creates with "discovered" (false by default; null on an
 *     update keeps the current value)
 */
public record AssetFreshnessSettingsInput(
    @JsonProperty("asset_stale_after_days") @NotNull @Min(0) @Max(3650) Integer staleAfterDays,
    @JsonProperty("asset_discovery_tag") Boolean discoveryTag) {}
