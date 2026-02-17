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
import java.util.Set;

/** Abstract base class providing common functionality for structured output processor handlers. */
public abstract class AbstractStructuredOutputProcessorHandler
    implements StructuredOutputProcessorHandler {

  protected final String label;
  protected final ContractOutputType type;
  protected final ContractOutputTechnicalType technicalType;
  protected final Set<ContractOutputField> fields;
  protected final boolean isFindingCompatible;
  protected final Set<ProcessingContext> supportedContexts;

  protected AbstractStructuredOutputProcessorHandler(
      String label,
      ContractOutputType type,
      ContractOutputTechnicalType technicalType,
      Set<ContractOutputField> fields,
      boolean isFindingCompatible,
      Set<ProcessingContext> supportedContexts) {
    this.label = label;
    this.type = type;
    this.technicalType = technicalType;
    this.fields = fields;
    this.isFindingCompatible = isFindingCompatible;
    this.supportedContexts = Collections.unmodifiableSet(supportedContexts);
  }

  @Override
  public String getLabel() {
    return label;
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
  public Set<ContractOutputField> getFields() {
    return fields;
  }

  @Override
  public boolean isFindingCompatible() {
    return isFindingCompatible;
  }

  @Override
  public Set<ProcessingContext> getSupportedContexts() {
    return supportedContexts;
  }

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
