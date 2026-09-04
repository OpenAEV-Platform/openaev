package io.openaev.rest.role.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Capability;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

/** Shared by the tenant role API and the platform role API. */
public record RoleOutput(
    @JsonProperty(ALIAS_ID) @NotBlank String id,
    @JsonProperty(ALIAS_NAME) @NotBlank String name,
    @JsonProperty(ALIAS_DESCRIPTION) String description,
    @JsonProperty(ALIAS_CAPABILITIES) Set<Capability> capabilities) {

  public static final String ALIAS_ID = "role_id";
  public static final String ALIAS_NAME = "role_name";
  public static final String ALIAS_DESCRIPTION = "role_description";
  public static final String ALIAS_CAPABILITIES = "role_capabilities";
}
