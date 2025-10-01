package io.openaev.rest.asset.security_platforms.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.rest.asset.form.AssetInput;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SecurityPlatformUpsertInput extends AssetInput {

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("security_platform_type")
  private SecurityPlatform.SECURITY_PLATFORM_TYPE securityPlatformType;

  @JsonProperty("security_platform_logo_light")
  private String logoLight;

  @JsonProperty("security_platform_logo_dark")
  private String logoDark;

  @JsonProperty("asset_external_reference")
  private String externalReference;
}
