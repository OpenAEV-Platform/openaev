package io.openaev.database.model;

import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.validator.routines.InetAddressValidator;

public enum ContractOutputType {
  @JsonProperty("text")
  Text(
      "text",
      ContractOutputTechnicalType.Text,
      null,
      true,
      Objects::nonNull,
      ContractOutputType::buildString,
      null,
      null,
      null),
  @JsonProperty("number")
  Number(
      "number",
      ContractOutputTechnicalType.Number,
      null,
      true,
      Objects::nonNull,
      ContractOutputType::buildString,
      null,
      null,
      null),
  @JsonProperty("port")
  Port(
      "port",
      ContractOutputTechnicalType.Number,
      null,
      true,
      Objects::nonNull,
      ContractOutputType::buildString,
      null,
      null,
      null),
  @JsonProperty("portscan")
  PortsScan(
      "portscan",
      ContractOutputTechnicalType.Object,
      new ArrayList<>(
          List.of(
              new ContractOutputField("asset_id", ContractOutputTechnicalType.Text, false),
              new ContractOutputField("host", ContractOutputTechnicalType.Text, true),
              new ContractOutputField("port", ContractOutputTechnicalType.Number, true),
              new ContractOutputField("service", ContractOutputTechnicalType.Text, true))),
      true,
      (JsonNode jsonNode) ->
          jsonNode.hasNonNull("host")
              && jsonNode.hasNonNull("port")
              && jsonNode.hasNonNull("service"),
      (JsonNode jsonNode) -> {
        String host = buildString(jsonNode, "host");
        String port = buildString(jsonNode, "port");
        String service = buildString(jsonNode, "service");
        return host + ":" + port + (hasText(service) ? " (" + service + ")" : "");
      },
      (JsonNode jsonNode) -> {
        if (jsonNode.get("asset_id") != null) {
          return List.of(jsonNode.get("asset_id").asText());
        }
        return new ArrayList<>();
      },
      null,
      null),
  @JsonProperty("ipv4")
  IPv4(
      "ipv4",
      ContractOutputTechnicalType.Text,
      null,
      true,
      (JsonNode jsonNode) ->
          InetAddressValidator.getInstance().isValidInet4Address(jsonNode.asText()),
      ContractOutputType::buildString,
      null,
      null,
      null),
  @JsonProperty("ipv6")
  IPv6(
      "ipv6",
      ContractOutputTechnicalType.Text,
      null,
      true,
      (JsonNode jsonNode) ->
          InetAddressValidator.getInstance().isValidInet6Address(jsonNode.asText()),
      ContractOutputType::buildString,
      null,
      null,
      null),
  @JsonProperty("credentials")
  Credentials(
      "credentials",
      ContractOutputTechnicalType.Object,
      new ArrayList<>(
          List.of(
              new ContractOutputField("username", ContractOutputTechnicalType.Text, true),
              new ContractOutputField("password", ContractOutputTechnicalType.Text, true))),
      true,
      (JsonNode jsonNode) -> jsonNode.hasNonNull("username") && jsonNode.hasNonNull("password"),
      (JsonNode jsonNode) -> {
        String username = buildString(jsonNode, "username");
        String password = buildString(jsonNode, "password");
        return username + ":" + password;
      },
      null,
      null,
      null),
  @JsonProperty("cve")
  CVE(
      "cve",
      ContractOutputTechnicalType.Object,
      new ArrayList<>(
          List.of(
              new ContractOutputField("asset_id", ContractOutputTechnicalType.Text, false),
              new ContractOutputField("id", ContractOutputTechnicalType.Text, true),
              new ContractOutputField("host", ContractOutputTechnicalType.Text, true),
              new ContractOutputField("severity", ContractOutputTechnicalType.Text, true))),
      true,
      (JsonNode jsonNode) ->
          jsonNode.hasNonNull("id")
              && jsonNode.hasNonNull("host")
              && jsonNode.hasNonNull("severity"),
      (JsonNode jsonNode) -> buildString(jsonNode, "id"),
      (JsonNode jsonNode) -> {
        JsonNode assetIdNode = jsonNode.get("asset_id");
        if (assetIdNode == null) {
          return Collections.emptyList();
        }
        if (assetIdNode.isArray()) {
          List<String> result = new ArrayList<>();
          for (JsonNode idNode : assetIdNode) {
            result.add(idNode.asText());
          }
          return result;
        } else {
          return List.of(assetIdNode.asText());
        }
      },
      null,
      null);

  public final String label;
  public final ContractOutputTechnicalType technicalType;
  public final List<ContractOutputField> fields;
  public final Boolean isFindingCompatible;
  public final Function<JsonNode, Boolean> validate;
  public final Function<JsonNode, String> toFindingValue;
  public final Function<JsonNode, List<String>> toFindingAssets;
  public final Function<JsonNode, List<String>> toFindingUsers;
  public final Function<JsonNode, List<String>> toFindingTeams;

  ContractOutputType(
      String label,
      ContractOutputTechnicalType technicalType,
      List<ContractOutputField> fields,
      Boolean isFindingCompatible,
      Function<JsonNode, Boolean> validate,
      Function<JsonNode, String> toFindingValue,
      Function<JsonNode, List<String>> toFindingAssets,
      Function<JsonNode, List<String>> toFindingUsers,
      Function<JsonNode, List<String>> toFindingTeams) {
    this.label = label;
    this.technicalType = technicalType;
    this.fields = fields; // used only for object, to declare the composition of the object
    this.isFindingCompatible = isFindingCompatible;
    this.validate = validate;
    this.toFindingValue = toFindingValue;
    this.toFindingAssets = toFindingAssets;
    this.toFindingUsers = toFindingUsers;
    this.toFindingTeams = toFindingTeams;
  }

  private static String buildString(@NotNull final JsonNode jsonNode) {
    if (jsonNode.isArray()) {
      List<String> values = new ArrayList<>();
      for (JsonNode element : jsonNode) {
        values.add(trimQuotes(element.asText()));
      }
      return String.join(" ", values);
    }
    return trimQuotes(jsonNode.asText());
  }

  private static String buildString(@NotNull final JsonNode jsonNode, @NotBlank final String key) {
    JsonNode valueNode = jsonNode.get(key);
    if (valueNode == null || valueNode.isNull()) {
      return "";
    }
    return buildString(valueNode);
  }

  private static String trimQuotes(@NotBlank final String value) {
    return value.replaceAll("^\"|\"$", "");
  }
}
