package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Asset freshness in this tenant: an asset unseen for this many days turns stale. 30 by default; 0
 * turns the check off.
 *
 * @param staleAfterDays days without an observation before an asset is stale (0: never)
 */
public record AssetFreshnessSettingsInput(
    @JsonProperty("asset_stale_after_days") @NotNull @Min(0) @Max(3650) Integer staleAfterDays) {}
