package io.openaev.api.platform.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PlatformGroupOutput(
    @JsonProperty("platform_group_id") @NotBlank String id,
    @JsonProperty("platform_group_name") @NotBlank String name,
    @JsonProperty("platform_group_description") String description) {}

