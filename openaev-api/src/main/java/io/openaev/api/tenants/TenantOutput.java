package io.openaev.api.tenants;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record TenantOutput(
    @JsonProperty("tenant_id") @NotBlank String id,
    @JsonProperty("tenant_name") @NotBlank String name,
    @JsonProperty("tenant_description") String description,
    @JsonProperty("tenant_deleted_at") Instant deletedAt) {}
