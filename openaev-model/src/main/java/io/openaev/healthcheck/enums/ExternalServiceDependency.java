package io.openaev.healthcheck.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ExternalServiceDependency {
  @JsonProperty("SMTP")
  SMTP("smtp"),
  @JsonProperty("IMAP")
  IMAP("imap"),
  @JsonProperty("NUCLEI")
  NUCLEI("openaev_nuclei"),
  @JsonProperty("NMAP")
  NMAP("openaev_nmap");

  private final String value;

  public String getValue() {
    return value;
  }

  ExternalServiceDependency(String value) {
    this.value = value;
  }

  public static ExternalServiceDependency fromValue(String value) {
    for (ExternalServiceDependency type : ExternalServiceDependency.values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    return null;
  }
}
