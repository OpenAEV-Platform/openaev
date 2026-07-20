package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Optional;

public enum PrimitiveType {
  @JsonProperty("asset_group_id")
  AssetGroupId("asset_group_id"),

  @JsonProperty("asset_id")
  AssetId("asset_id"),

  @JsonProperty("cve")
  CVE("cve"),

  @JsonProperty("document")
  Document("document"),

  @JsonProperty("domain")
  Domain("domain"),

  @JsonProperty("hash")
  Hash("hash"),

  @JsonProperty("host")
  Host("host"),

  @JsonProperty("hostname")
  Hostname("hostname"),

  @JsonProperty("ipv4")
  IPv4("ipv4"),

  @JsonProperty("ipv6")
  IPv6("ipv6"),

  @JsonProperty("ip_subnet")
  IpSubnet("ip_subnet"),

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

  @JsonProperty("targeted-asset")
  TargetedAsset("targeted-asset"),

  @JsonProperty("text")
  Text("text"),

  @JsonProperty("username")
  Username("username");

  public final String label;

  PrimitiveType(String label) {
    this.label = label;
  }

  public static PrimitiveType fromLabel(String label) {
    return fromLabelOptional(label)
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
