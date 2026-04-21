package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum ConditionKeyType {
  @JsonProperty("execution_time")
  EXECUTION_TIME,

  @JsonProperty("step_template_id")
  STEP_TEMPLATE_ID,

  @JsonProperty("text")
  Text,

  @JsonProperty("status")
  Status,

  @JsonProperty("number")
  Number,

  @JsonProperty("port")
  Port,

  @JsonProperty("portscan")
  Portscan,

  @JsonProperty("ipv4")
  IPv4,

  @JsonProperty("ipv6")
  IPv6,

  @JsonProperty("credentials")
  Credentials,

  @JsonProperty("cve")
  CVE,

  @JsonProperty("username")
  Username,

  @JsonProperty("share")
  Share,

  @JsonProperty("admin_username")
  AdminUsername,

  @JsonProperty("group")
  Group,

  @JsonProperty("computer")
  Computer,

  @JsonProperty("password_policy")
  PasswordPolicy,

  @JsonProperty("delegation")
  Delegation,

  @JsonProperty("sid")
  Sid,

  @JsonProperty("vulnerability")
  Vulnerability,

  @JsonProperty("asset")
  Asset;
}
