package io.openaev.api.markings;

import io.openaev.api.markings.form.MarkingDefinitionInput;
import io.openaev.api.markings.response.MarkingDefinitionOutput;
import io.openaev.database.model.MarkingDefinition;

public class MarkingDefinitionMapper {

  private MarkingDefinitionMapper() {}

  /** Applies an input onto an entity. Tenant attribution is the caller's job (v2 isolation). */
  public static MarkingDefinition apply(MarkingDefinition marking, MarkingDefinitionInput input) {
    marking.setType(input.type());
    marking.setName(input.name());
    marking.setOrder(input.order());
    marking.setColor(input.color());
    return marking;
  }

  public static MarkingDefinitionOutput toOutput(MarkingDefinition marking) {
    return new MarkingDefinitionOutput(
        marking.getId(),
        marking.getType(),
        marking.getName(),
        marking.getOrder(),
        marking.getColor());
  }
}
