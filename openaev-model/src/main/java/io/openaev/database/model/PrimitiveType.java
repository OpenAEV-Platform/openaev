package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

public enum PrimitiveType {
  @JsonProperty("text")
  Text("text"),

  @JsonProperty("number")
  Number("number"),

  @JsonProperty("host")
  Host("host"),

  @JsonProperty("hostname")
  Hostname("hostname"),

  @JsonProperty("domain")
  Domain("domain"),

  @JsonProperty("ipv4")
  IPv4("ipv4"),

  @JsonProperty("ipv6")
  IPv6("ipv6"),

  @JsonProperty("port")
  Port("port"),

  @JsonProperty("username")
  Username("username"),

  @JsonProperty("password")
  Password("password"),

  @JsonProperty("hash")
  Hash("hash"),

  @JsonProperty("service")
  Service("service"),

  @JsonProperty("cve")
  CVE("cve"),

  @JsonProperty("severity")
  Severity("severity"),

  @JsonProperty("share_name")
  ShareName("share_name"),

  @JsonProperty("permissions")
  Permissions("permissions"),

  @JsonProperty("document")
  Document("document"),

  @JsonProperty("targeted-asset")
  TargetedAsset("targeted-asset");

  public final String label;

  PrimitiveType(String label) {
    this.label = label;
  }

  public static PrimitiveType fromLabel(String label) {
    return Arrays.stream(values())
        .filter(v -> v.label.equals(label))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown PrimitiveType label: '"
                        + label
                        + "'. Valid values: "
                        + Arrays.toString(values())));
  }
}
