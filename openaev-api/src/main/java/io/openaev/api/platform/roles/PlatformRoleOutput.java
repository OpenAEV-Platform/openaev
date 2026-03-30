package io.openaev.api.platform.roles;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PlatformRoleOutput(
    @JsonProperty(ALIAS_ID) @NotBlank String id,
    @JsonProperty(ALIAS_NAME) @NotBlank String name,
    @JsonProperty(ALIAS_DESCRIPTION) String description) {

  public static final String ALIAS_ID = "platform_role_id";
  public static final String ALIAS_NAME = "platform_role_name";
  public static final String ALIAS_DESCRIPTION = "platform_role_description";
}
