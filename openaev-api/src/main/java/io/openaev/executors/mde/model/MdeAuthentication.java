package io.openaev.executors.mde.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

/**
 * Bearer token response from Azure AD. Using {@code @Getter} only to prevent {@code toString()}
 * from exposing the token.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdeAuthentication {

  private String access_token;
  private int expires_in;
}
