package io.openaev.rest.asset.security_platforms.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.SecurityPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

/**
 * Lightweight security platform projection for remediation gating endpoints: only what the
 * remediation tabs need (id, name, type), instead of serializing the full JPA entity graph (traces,
 * collectors, logos...).
 */
@Builder
@Data
public class SecurityPlatformSimpleOutput {

  @JsonProperty("asset_id")
  @Schema(description = "Security platform id")
  @NotBlank
  private String id;

  @JsonProperty("asset_name")
  @Schema(description = "Security platform name")
  @NotBlank
  private String name;

  @JsonProperty("security_platform_type")
  @Schema(description = "Security platform type")
  @NotNull
  private SecurityPlatform.SECURITY_PLATFORM_TYPE securityPlatformType;
}
