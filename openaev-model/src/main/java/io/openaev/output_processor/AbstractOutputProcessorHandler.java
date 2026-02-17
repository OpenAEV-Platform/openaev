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

/** Abstract base class providing common functionality for structured output processor handlers. */
public abstract class AbstractOutputProcessorHandler implements OutputProcessorHandler {

  protected final ContractOutputType type;
  protected final ContractOutputTechnicalType technicalType;
  protected final List<ContractOutputField> fields;
  protected final boolean isFindingCompatible;
  protected final List<ProcessingContext> supportedContexts;

  protected AbstractOutputProcessorHandler(
      ContractOutputType type,
      ContractOutputTechnicalType technicalType,
      List<ContractOutputField> fields,
      boolean isFindingCompatible,
      List<ProcessingContext> supportedContexts) {
    this.type = type;
    this.technicalType = technicalType;
    this.fields = fields;
    this.isFindingCompatible = isFindingCompatible;
    this.supportedContexts = Collections.unmodifiableList(supportedContexts);

    validateContextInterfaces();
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
  public List<ProcessingContext> getSupportedContexts() {
    return supportedContexts;
  }

  private void validateContextInterfaces() {
    if (supportedContexts.contains(ProcessingContext.FINDING)) {
      if (!(this instanceof FindingCapable)) {
        throw new IllegalStateException(
            type.getLabel() + " declares FINDING context but does not implement FindingCapable");
      }
    }
    if (supportedContexts.contains(ProcessingContext.ASSET)) {
      if (!(this instanceof AssetCapable)) {
        throw new IllegalStateException(
            type.getLabel() + " declares ASSET context but does not implement AssetCapable");
      }
    }
    if (supportedContexts.contains(ProcessingContext.EXPECTATION)) {
      if (!(this instanceof ExpectationCapable)) {
        throw new IllegalStateException(
            type.getLabel()
                + " declares EXPECTATION context but does not implement ExpectationCapable");
      }
    }
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
