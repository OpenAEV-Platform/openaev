package io.openaev.api.groups.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Grant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** A grant of a tenant group, with the fields of the {@link Grant} entity. */
public record TenantGroupGrantOutput(
    @JsonProperty("grant_id") @NotBlank String id,
    @JsonProperty("grant_name") @NotNull Grant.GRANT_TYPE name,
    @JsonProperty("grant_group") String groupId,
    @JsonProperty("grant_resource") String resourceId,
    @JsonProperty("grant_resource_type") Grant.GRANT_RESOURCE_TYPE resourceType) {}
