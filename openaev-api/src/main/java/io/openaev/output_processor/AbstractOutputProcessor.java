package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Abstract base class providing common functionality for structured output processor handlers. */
@Slf4j
public abstract class AbstractOutputProcessor implements OutputProcessor {

  protected final ContractOutputType type;
  protected final ContractOutputTechnicalType technicalType;
  protected final List<ContractOutputField> fields;
  protected final boolean isFindingCompatible;

  protected AbstractOutputProcessor(
      ContractOutputType type,
      ContractOutputTechnicalType technicalType,
      List<ContractOutputField> fields,
      boolean isFindingCompatible) {
    this.type = type;
    this.technicalType = technicalType;
    this.fields = fields;
    this.isFindingCompatible = isFindingCompatible;
  }

  @Override
  public ContractOutputType getType() {
    return type;
  }

  @Override
  public ContractOutputTechnicalType getTechnicalType() {
    return technicalType;
  }

  @Override
  public List<ContractOutputField> getFields() {
    return fields;
  }

  @Override
  public boolean isFindingCompatible() {
    return isFindingCompatible;
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode != null;
  }

  // FINDING METHODS
  // Override these in handlers that support findings

  /**
   * Convert JSON node to finding value string. Override this method if handler supports findings.
   * Default returns empty string with warning log.
   */
  @Override
  public String toFindingValue(JsonNode jsonNode) {
    log.warn("Handler {} does not implement toFindingValue, returning empty string", type);
    return "";
  }

  /**
   * Extract asset IDs from JSON node for finding linking. Override to provide custom logic, default
   * returns empty list.
   */
  public List<String> toFindingAssets(JsonNode jsonNode) {
    log.warn("Handler {} does not implement toFindingAssets, returning an empty list", type);
    return Collections.emptyList();
  }

  /**
   * Extract user IDs from JSON node for finding linking. Override to provide custom logic, default
   * returns empty list.
   */
  public List<String> toFindingUsers(JsonNode jsonNode) {
    log.warn("Handler {} does not implement toFindingUsers, returning an empty list", type);
    return Collections.emptyList();
  }

  /**
   * Extract team IDs from JSON node for finding linking. Override to provide custom logic, default
   * returns empty list.
   */
  public List<String> toFindingTeams(JsonNode jsonNode) {
    log.warn("Handler {} does not implement toFindingTeams, returning an empty list", type);
    return Collections.emptyList();
  }

  // Utility methods
  protected String buildString(@NotNull final JsonNode jsonNode) {
    if (jsonNode.isArray()) {
      List<String> values = new ArrayList<>();
      for (JsonNode element : jsonNode) {
        values.add(trimQuotes(element.asText()));
      }
      return String.join(" ", values);
    }
    return trimQuotes(jsonNode.asText());
  }

  protected String buildString(@NotNull final JsonNode jsonNode, @NotBlank final String key) {
    JsonNode valueNode = jsonNode.get(key);
    if (valueNode == null || valueNode.isNull()) {
      return "";
    }
    return buildString(valueNode);
  }

  protected String trimQuotes(@NotBlank final String value) {
    return value.replaceAll("^\"|\"$", "");
  }
}
