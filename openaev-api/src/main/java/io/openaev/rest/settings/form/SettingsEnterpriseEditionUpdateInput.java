package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsEnterpriseEditionUpdateInput {
  @JsonProperty("platform_enterprise_license")
  @Schema(description = "cert of enterprise edition")
  private String enterpriseEdition;
}
