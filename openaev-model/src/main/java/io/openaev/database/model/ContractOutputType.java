package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Hidden;

public enum ContractOutputType {
  @JsonProperty("text")
  Text("text"),

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

  @JsonProperty("asreproastable_account")
  AsreproastableAccount("asreproastable_account"),

  @JsonProperty("kerberoastable_account")
  KerberoastableAccount("kerberoastable_account"),

  @Hidden
  @JsonProperty("asset")
  Asset("asset");

  private final String label;

  ContractOutputType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
