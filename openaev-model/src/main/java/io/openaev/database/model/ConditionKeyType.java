package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum ConditionKeyType {
  @JsonProperty("execution_time")
  ExecutionTime("execution_time"),

  @JsonProperty("step_template_id")
  StepTemplateId("step_template_id"),

  @JsonProperty("text")
  Text("text"),

  @JsonProperty("status")
  Status("status"),

  @JsonProperty("number")
  Number("number"),

  @JsonProperty("port")
  Port("port"),

  @JsonProperty("portscan")
  PortsScan("portscan"),

  @JsonProperty("ipv4")
  IPv4("ipv4"),

  @JsonProperty("ipv6")
  IPv6("ipv6"),

  @JsonProperty("credentials")
  Credentials("credentials"),

  @JsonProperty("cve")
  CVE("cve"),

  @JsonProperty("username")
  Username("username"),

  @JsonProperty("share")
  Share("share"),

  @JsonProperty("admin_username")
  AdminUsername("admin_username"),

  @JsonProperty("group")
  Group("group"),

  @JsonProperty("computer")
  Computer("computer"),

  @JsonProperty("password_policy")
  PasswordPolicy("password_policy"),

  @JsonProperty("delegation")
  Delegation("delegation"),

  @JsonProperty("sid")
  Sid("sid"),

  @JsonProperty("vulnerability")
  Vulnerability("vulnerability"),

  @JsonProperty("account_with_password_not_required")
  AccountWithPasswordNotRequired("account_with_password_not_required"),

  @JsonProperty("asset")
  Asset("asset");

  private final String label;

  ConditionKeyType(String label) {
    this.label = label;
  }
}
