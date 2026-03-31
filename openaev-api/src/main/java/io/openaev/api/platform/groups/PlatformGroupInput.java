package io.openaev.api.platform.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PlatformGroupInput(
    @JsonProperty("platform_group_name") @NotBlank String name,
    @JsonProperty("platform_group_description") String description) {}
