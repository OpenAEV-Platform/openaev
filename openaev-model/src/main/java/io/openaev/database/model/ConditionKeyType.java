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
  TEXT,

  @JsonProperty("status")
  STATUS,

  @JsonProperty("number")
  NUMBER,

  @JsonProperty("port")
  PORT,

  @JsonProperty("portscan")
  PORTSCAN,

  @JsonProperty("ipv4")
  IPV4,

  @JsonProperty("ipv6")
  IPV6,

  @JsonProperty("credentials")
  CREDENTIALS,

  @JsonProperty("cve")
  CVE,

  @JsonProperty("username")
  USERNAME,

  @JsonProperty("share")
  SHARE,

  @JsonProperty("admin_username")
  ADMIN_USERNAME,

  @JsonProperty("group")
  GROUP,

  @JsonProperty("computer")
  COMPUTER,

  @JsonProperty("password_policy")
  PASSWORD_POLICY,

  @JsonProperty("delegation")
  DELEGATION,

  @JsonProperty("sid")
  SID,

  @JsonProperty("vulnerability")
  VULNERABILITY,

  @JsonProperty("asset")
  ASSET;
}
