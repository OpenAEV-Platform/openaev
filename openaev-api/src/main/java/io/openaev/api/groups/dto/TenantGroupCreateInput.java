package io.openaev.api.groups.dto;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantGroupCreateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("group_name")
  private String name;

  @JsonProperty("group_description")
  private String description;

  @JsonProperty("group_default_user_assign")
  private boolean defaultUserAssignation;
}
