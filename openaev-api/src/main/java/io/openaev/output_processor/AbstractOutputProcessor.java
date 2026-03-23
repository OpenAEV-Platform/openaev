package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/** Abstract base class providing common functionality for structured output processor handlers. */
public abstract class AbstractOutputProcessor implements OutputProcessor {

  protected final ContractOutputType type;
  protected final ContractOutputTechnicalType technicalType;
  protected final List<ContractOutputField> fields;

  protected AbstractOutputProcessor(
      ContractOutputType type,
      ContractOutputTechnicalType technicalType,
      List<ContractOutputField> fields) {
    this.type = type;
    this.technicalType = technicalType;
    this.fields = fields;
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
  public boolean validate(JsonNode jsonNode) {
    return jsonNode != null;
  }

  // UTILITY methods

  /**
   * Builds a string representation from a JSON node.
   *
   * <p>If the node is an array, concatenates all elements (with quotes trimmed) separated by
   * spaces. Otherwise, returns the node's text value with quotes trimmed.
   *
   * @param jsonNode the JSON node to process
   * @return a string representation of the node's value(s)
   */
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

  /**
   * Builds a string representation from a specific key in a JSON node.
   *
   * <p>If the key is missing or null, returns an empty string. Otherwise, delegates to {@link
   * #buildString(JsonNode)}.
   *
   * @param jsonNode the JSON node to process
   * @param key the key to extract
   * @return a string representation of the value at the given key, or empty string if not present
   */
  protected String buildString(@NotNull final JsonNode jsonNode, @NotBlank final String key) {
    JsonNode valueNode = jsonNode.get(key);
    if (valueNode == null || valueNode.isNull()) {
      return "";
    }
    return buildString(valueNode);
  }

  /**
   * Removes leading and trailing quotes from a string value.
   *
   * @param value the string to trim
   * @return the string without leading/trailing quotes
   */
  protected String trimQuotes(@NotBlank final String value) {
    return value.replaceAll("^\"|\"$", "");
  }
}
