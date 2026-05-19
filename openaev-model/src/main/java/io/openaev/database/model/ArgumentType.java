package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;

/**
 * Enumeration of supported argument types for {@link PayloadArgument}.
 *
 * <p>Types that expose subtypes (e.g. {@link #PortsScan}, {@link #Credentials}) can pair with an
 * {@link ArgumentSubType} to address a specific field. Types whose processor has an empty field
 * list ({@link #Text}, {@link #Number}, {@link #Port}, {@link #IPv4}, {@link #IPv6}) do not require
 * a subtype.
 */
public enum ArgumentType {
  @JsonProperty("text")
  Text("text"),

  @JsonProperty("number")
  Number("number"),

  @JsonProperty("port")
  Port("port"),

  @JsonProperty("portscan")
  PortsScan(
      "portscan", List.of(ArgumentSubType.Host, ArgumentSubType.Port, ArgumentSubType.Service)),

  @JsonProperty("ipv4")
  IPv4("ipv4"),

  @JsonProperty("ipv6")
  IPv6("ipv6"),

  @JsonProperty("credentials")
  Credentials("credentials", List.of(ArgumentSubType.Username, ArgumentSubType.Password)),

  @JsonProperty("cve")
  CVE("cve", List.of(ArgumentSubType.Host, ArgumentSubType.Severity)),

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

  // -- Argument-only types --

  @JsonProperty("document")
  Document("document"),

  @JsonProperty("targeted-asset")
  TargetedAsset("targeted-asset");

  public final String label;
  public final List<ArgumentSubType> subTypes;

  ArgumentType(String label) {
    this(label, List.of());
  }

  ArgumentType(String label, List<ArgumentSubType> subTypes) {
    this.label = label;
    this.subTypes = subTypes;
  }

  /**
   * Looks up an {@link ArgumentType} by its JSON label. Used for manual JSON parsing (e.g. {@code
   * PayloadUtils.buildPayload}). Jackson uses the {@code @JsonProperty} annotations on the
   * constants directly.
   *
   * @param label the raw string value
   * @return the matching enum constant
   * @throws IllegalArgumentException when no constant matches
   */
  public static ArgumentType fromLabel(String label) {
    return Arrays.stream(values())
        .filter(v -> v.label.equals(label))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown ArgumentType label: '"
                        + label
                        + "'. Valid values: "
                        + Arrays.toString(values())));
  }
}
