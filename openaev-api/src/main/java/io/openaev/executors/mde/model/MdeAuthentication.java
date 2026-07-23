package io.openaev.executors.mde.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

// @Getter only (no @Data/@ToString) so the bearer token is never exposed via toString().
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdeAuthentication {

  private String access_token;
  private int expires_in;
}
