package io.openaev.api.groups.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PlatformGroupOutput(
    @JsonProperty(ALIAS_ID) @NotBlank String id,
    @JsonProperty(ALIAS_NAME) @NotBlank String name,
    @JsonProperty(ALIAS_DESCRIPTION) String description,
    @JsonProperty(ALIAS_DEFAULT_USER_ASSIGN) boolean defaultUserAssignation) {

  public static final String ALIAS_ID = "platform_group_id";
  public static final String ALIAS_NAME = "platform_group_name";
  public static final String ALIAS_DESCRIPTION = "platform_group_description";
  public static final String ALIAS_DEFAULT_USER_ASSIGN = "group_default_user_assign";
}
