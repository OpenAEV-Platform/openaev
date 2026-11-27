package io.openaev.api.xtm_peer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class XtmPeerTokenOutput {
  @JsonProperty("user_id")
  private String userId;

  @JsonProperty("email")
  private String email;

  @JsonProperty("name")
  private String name;

  @JsonProperty("token_name")
  private String tokenName;

  @JsonProperty("token")
  private String token;
}
