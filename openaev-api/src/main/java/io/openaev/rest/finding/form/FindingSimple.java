package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Builder;

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
    @JsonProperty("finding_last_seen") @NotNull Instant lastSeen) {}
