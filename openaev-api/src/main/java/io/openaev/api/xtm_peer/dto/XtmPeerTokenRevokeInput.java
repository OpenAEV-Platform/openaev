package io.openaev.api.xtm_peer.dto;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class XtmPeerTokenRevokeInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @Size(max = 320)
  @Schema(description = "The email identifying the service account")
  private String email;

  @NotBlank(message = MANDATORY_MESSAGE)
  @Size(max = 200)
  @JsonProperty("token_name")
  @Schema(description = "The name of the token to revoke")
  private String tokenName;
}
