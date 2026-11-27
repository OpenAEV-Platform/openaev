package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.rest.inject.output.InjectSimple;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;

/**
 * One row of a finding search. The components after {@code lastSeen} are deprecated: they are the
 * fields the search rows carried before a finding became one row per (type, value, location), kept
 * so the API clients that read them keep working. New code reads {@code finding_asset} and the
 * occurrences of the finding.
 */
@Builder
public record FindingSimple(
    @JsonProperty("finding_id") @NotBlank String id,
    @Schema(
            description = "Represents the data type being extracted.",
            example = "text, number, port, portscan, ipv4, ipv6, credentials, cve")
        @JsonProperty("finding_type")
        @NotNull
        ContractOutputType type,
    @Schema(
            description =
                "Finding value. Masked when the finding type holds secret material: the API never"
                    + " discloses the cleartext value of a sensitive finding.")
        @JsonProperty("finding_value")
        @NotBlank
        String value,
    @Schema(description = "Asset the finding was detected on, null when it is not on an asset")
        @JsonProperty("finding_asset")
        EndpointSimple asset,
    @JsonProperty("finding_first_seen") @NotNull Instant firstSeen,
    @JsonProperty("finding_last_seen") @NotNull Instant lastSeen,
    @Schema(
            deprecated = true,
            description =
                "Deprecated: when the finding row was created, a storage time. Read"
                    + " finding_first_seen for the detection.")
        @JsonProperty("finding_created_at")
        Instant creationDate,
    @Schema(
            deprecated = true,
            description =
                "Deprecated: when the finding row was last written, any write moves it. Read"
                    + " finding_last_seen for the last detection.")
        @JsonProperty("finding_updated_at")
        Instant updateDate,
    @Schema(
            deprecated = true,
            description =
                "Deprecated: assets linked to the finding (any asset type), use finding_asset.")
        @JsonProperty("finding_assets")
        @NotNull
        Set<EndpointSimple> assets,
    @Schema(
            deprecated = true,
            description = "Deprecated: asset groups that contain an asset of the finding.")
        @JsonProperty("finding_asset_groups")
        Set<AssetGroupSimple> assetGroups,
    @Schema(
            deprecated = true,
            description =
                "Deprecated: inject of the latest occurrence, search the occurrences of the"
                    + " finding instead.")
        @JsonProperty("finding_inject")
        @JsonInclude(NON_NULL)
        InjectSimple inject,
    @Schema(
            deprecated = true,
            description = "Deprecated: simulation of the latest occurrence's inject.")
        @JsonProperty("finding_simulation")
        @JsonInclude(NON_NULL)
        ExerciseSimple simulation,
    @Schema(
            deprecated = true,
            description = "Deprecated: scenario of the latest occurrence's simulation.")
        @JsonProperty("finding_scenario")
        @JsonInclude(NON_NULL)
        ScenarioSimple scenario,
    @Schema(deprecated = true, description = "Deprecated: teams linked to the finding.")
        @JsonProperty("finding_teams")
        Set<TargetSimple> teams,
    @Schema(deprecated = true, description = "Deprecated: players linked to the finding.")
        @JsonProperty("finding_users")
        Set<TargetSimple> users) {}
