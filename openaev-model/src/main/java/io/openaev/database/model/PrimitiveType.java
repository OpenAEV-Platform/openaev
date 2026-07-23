package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public enum PrimitiveType {
  @JsonProperty("account_with_password_not_required")
  AccountWithPasswordNotRequired("account_with_password_not_required"),

  @JsonProperty("admin_username")
  AdminUsername("admin_username"),

  @JsonProperty("asreproastable_account")
  AsreproastableAccount("asreproastable_account"),

  @JsonProperty("asset_group_id")
  AssetGroupId("asset_group_id"),

  @JsonProperty("asset_id")
  AssetId("asset_id"),

  @JsonProperty("computer_name")
  ComputerName("computer_name"),

  @JsonProperty("cve")
  CVE("cve"),

  @JsonProperty("delegation_account")
  DelegationAccount("delegation_account"),

  @JsonProperty("document")
  Document("document"),

  @JsonProperty("domain")
  Domain("domain"),

  @JsonProperty("group_name")
  GroupName("group_name"),

  @JsonProperty("hash")
  Hash("hash"),

  @JsonProperty("host")
  Host("host"),

  @JsonProperty("ipv4")
  IPv4("ipv4"),

  @JsonProperty("ipv6")
  IPv6("ipv6"),

  @JsonProperty("ip_subnet")
  IpSubnet("ip_subnet"),

  @JsonProperty("kerberoastable_account")
  KerberoastableAccount("kerberoastable_account"),

  @JsonProperty("key")
  Key("key"),

  @JsonProperty("number")
  Number("number"),

  @JsonProperty("password")
  Password("password"),

  @JsonProperty("permissions")
  Permissions("permissions"),

  @JsonProperty("port")
  Port("port"),

  @JsonProperty("service")
  Service("service"),

  @JsonProperty("severity")
  Severity("severity"),

  @JsonProperty("share_name")
  ShareName("share_name"),

  @JsonProperty("sid")
  SID("sid"),

  @JsonProperty("targeted-asset")
  TargetedAsset("targeted-asset"),

  @JsonProperty("text")
  Text("text"),

  @JsonProperty("username")
  Username("username"),

  @JsonProperty("value")
  Value("value"),

  @JsonProperty("vulnerability_name")
  VulnerabilityName("vulnerability_name"),

  @JsonProperty("vulnerability_status")
  VulnerabilityStatus("vulnerability_status");

  public final String label;

  /**
   * Labels of the pre-#6536 {@code ArgumentType} enum that no longer exist in {@code
   * PrimitiveType}, mapped to their closest primitive. Payload rows created before the chaining
   * refactor still carry these labels in {@code payloads.payload_arguments} and {@code
   * injects_statuses.status_payload_output}; without this mapping Jackson fails to deserialize the
   * column and the whole entity row becomes unreadable (hypersistence JsonType then rethrows as
   * "cannot be transformed to Json object"). A Flyway migration rewrites stored data, but this
   * keeps reads safe for anything written between deploy and migration, and for external clients
   * (injector contracts, imports) still sending legacy labels.
   */
  private static final Map<String, String> LEGACY_ARGUMENT_TYPE_LABELS =
      Map.ofEntries(
          Map.entry("credentials", "username"),
          Map.entry("portscan", "port"),
          Map.entry("share", "share_name"),
          Map.entry("admin_username", "admin_username"),
          Map.entry("group", "group_name"),
          Map.entry("computer", "computer_name"),
          Map.entry("password_policy", "text"),
          Map.entry("delegation", "delegation_account"),
          Map.entry("vulnerability", "cve"),
          Map.entry("asreproastable_account", "asreproastable_account"),
          Map.entry("kerberoastable_account", "kerberoastable_account"));

  PrimitiveType(String label) {
    this.label = label;
  }

  @JsonCreator
  public static PrimitiveType fromLabel(String label) {
    // Immutable maps reject null keys: guard so a null label keeps failing with the controlled
    // IllegalArgumentException below instead of an opaque NullPointerException.
    String effectiveLabel =
        label == null ? null : LEGACY_ARGUMENT_TYPE_LABELS.getOrDefault(label, label);
    return fromLabelOptional(effectiveLabel)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown PrimitiveType label: '"
                        + label
                        + "'. Valid values: "
                        + Arrays.toString(values())));
  }

  public static Optional<PrimitiveType> fromLabelOptional(String label) {
    return Arrays.stream(values()).filter(v -> v.label.equals(label)).findFirst();
  }
}
