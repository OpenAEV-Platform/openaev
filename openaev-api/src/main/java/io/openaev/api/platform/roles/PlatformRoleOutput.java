package io.openaev.api.platform.roles;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Capability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;

public record PlatformRoleOutput(
    @JsonProperty("platform_role_id") @NotBlank String id,
    @JsonProperty("platform_role_name") @NotBlank String name,
    @JsonProperty("platform_role_description") String description,
    @JsonProperty("platform_role_capabilities") Set<Capability> capabilities,
    @JsonProperty("platform_role_created_at") @NotNull Instant createdAt,
    @JsonProperty("platform_role_updated_at") @NotNull Instant updatedAt) {}
