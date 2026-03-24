package io.openaev.api.capabilities;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

@Schema(description = "A capability node in the capability tree")
public record CapabilityOutput(
    @JsonProperty("capability_value")
        @Schema(description = "Enum key of the capability or group")
        @NotBlank
        String value,
    @JsonProperty("capability_checkable")
        @Schema(description = "Whether this capability can be assigned to a role")
        @NotNull
        boolean checkable,
    @JsonProperty("capability_scopes")
        @Schema(description = "Scopes where this capability applies (PLATFORM, TENANT)")
        @NotBlank
        Set<String> scopes,
    @JsonProperty("capability_children") @Schema(description = "Child capabilities") @NotBlank
        List<CapabilityOutput> children) {}
