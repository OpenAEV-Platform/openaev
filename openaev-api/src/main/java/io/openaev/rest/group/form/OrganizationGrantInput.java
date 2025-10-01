package io.openaev.rest.group.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrganizationGrantInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("organization_id")
  private String organizationId;
}
