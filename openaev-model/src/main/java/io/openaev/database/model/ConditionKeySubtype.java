package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum ConditionKeySubtype {
  @JsonProperty("port")
  PORT,

  @JsonProperty("ipv4")
  IPV4,

  @JsonProperty("ipv6")
  IPV6,

  @JsonProperty("username")
  USERNAME,

  @JsonProperty("password")
  PASSWORD;
}
