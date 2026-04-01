package io.openaev.api.platform.roles;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Capability;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record PlatformRoleInput(
    @JsonProperty("platform_role_name") @NotBlank String name,
    @JsonProperty("platform_role_description") String description,
    @JsonProperty("platform_role_capabilities") Set<Capability> capabilities) {}
