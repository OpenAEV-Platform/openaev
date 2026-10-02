package io.openaev.api.marking_definition;

import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.api.marking_definition.form.MarkingDefinitionOutput;
import io.openaev.database.model.MarkingDefinition;

public final class MarkingDefinitionMapper {

  private MarkingDefinitionMapper() {}

  public static MarkingDefinitionOutput toOutput(MarkingDefinition entity) {
    return new MarkingDefinitionOutput(
        entity.getId(),
        entity.getType(),
        entity.getDefinition(),
        entity.getColor(),
        entity.getOrder(),
        entity.getProtectedDefinition(),
        entity.getCreatedAt());
  }

  public static MarkingDefinition fromInput(MarkingDefinitionInput input) {
    MarkingDefinition entity = new MarkingDefinition();
    entity.setType(input.type());
    entity.setDefinition(input.definition());
    entity.setColor(input.color());
    entity.setOrder(input.order());
    return entity;
  }
}
