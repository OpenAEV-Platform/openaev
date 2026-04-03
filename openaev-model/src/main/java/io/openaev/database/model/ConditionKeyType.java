package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum ConditionKeyType {
  @JsonProperty("execution_time")
  EXECUTION_TIME("execution_time"),

  @JsonProperty("step_template_id")
  STEP_TEMPLATE_ID("step_template_id"),

  @JsonProperty("text")
  TEXT("text"),

  @JsonProperty("status")
  STATUS("status"),

  @JsonProperty("number")
  NUMBER("number"),

  @JsonProperty("port")
  PORT("port"),

  @JsonProperty("portscan")
  PORTSCAN("portscan"),

  @JsonProperty("ipv4")
  IPV4("ipv4"),

  @JsonProperty("ipv6")
  IPV6("ipv6"),

  @JsonProperty("credentials")
  CREDENTIALS("credentials"),

  @JsonProperty("cve")
  CVE("cve"),

  @JsonProperty("username")
  USERNAME("username"),

  @JsonProperty("share")
  SHARE("share"),

  @JsonProperty("admin_username")
  ADMIN_USERNAME("admin_username"),

  @JsonProperty("group")
  GROUP("group"),

  @JsonProperty("computer")
  COMPUTER("computer"),

  @JsonProperty("password_policy")
  PASSWORD_POLICY("password_policy"),

  @JsonProperty("delegation")
  DELEGATION("delegation"),

  @JsonProperty("sid")
  SID("sid"),

  @JsonProperty("vulnerability")
  VULNERABILITY("vulnerability"),

  @JsonProperty("asset")
  ASSET("asset");

  private final String label;

  ConditionKeyType(String label) {
    this.label = label;
  }
}
