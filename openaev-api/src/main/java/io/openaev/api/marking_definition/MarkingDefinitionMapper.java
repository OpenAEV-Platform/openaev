package io.openaev.api.marking_definition;

import io.openaev.api.marking_definition.dto.MarkingDefinitionCsvRow;
import io.openaev.api.marking_definition.form.MarkingDefinitionOutput;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.utils.CsvInjectionUtils;

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

  public static MarkingDefinitionCsvRow toCsvRow(MarkingDefinition entity) {
    MarkingDefinitionCsvRow row = new MarkingDefinitionCsvRow();
    row.setType(CsvInjectionUtils.neutralise(entity.getType()));
    row.setDefinition(CsvInjectionUtils.neutralise(entity.getDefinition()));
    row.setColor(entity.getColor());
    row.setOrder(String.valueOf(entity.getOrder()));
    return row;
  }
}
