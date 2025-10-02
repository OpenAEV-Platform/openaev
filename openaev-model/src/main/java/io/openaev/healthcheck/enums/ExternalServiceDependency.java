package io.openaev.healthcheck.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ExternalServiceDependency {
  @JsonProperty("SMTP")
  SMTP,
  @JsonProperty("IMAP")
  IMAP,
}
