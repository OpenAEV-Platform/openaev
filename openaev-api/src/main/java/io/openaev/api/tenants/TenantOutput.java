package io.openaev.api.tenants;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record TenantOutput(
    @JsonProperty(ALIAS_ID) @NotBlank String id,
    @JsonProperty(ALIAS_NAME) @NotBlank String name,
    @JsonProperty(ALIAS_DESCRIPTION) String description,
    @JsonProperty(ALIAS_DELETED_AT) Instant deletedAt,
    @JsonProperty("protected_resource") @NotNull Boolean protectedResource) {

  public static final String ALIAS_ID = "tenant_id";
  public static final String ALIAS_NAME = "tenant_name";
  public static final String ALIAS_DESCRIPTION = "tenant_description";
  public static final String ALIAS_DELETED_AT = "tenant_deleted_at";
}
