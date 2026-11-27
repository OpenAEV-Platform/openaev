package io.openaev.api.marking_definition;

import io.openaev.api.marking_definition.dto.MarkingDefinitionCsvRow;
import io.openaev.api.marking_definition.form.MarkingDefinitionOutput;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
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
        entity.getCreatedAt(),
        entity.getStixId());
  }

  /** A definition the tenant can edit: only the default markings of a tenant are protected. */
  public static MarkingDefinition toEntity(
      String tenantId, String type, String definition, String color, int order, String stixId) {
    MarkingDefinition entity = new MarkingDefinition();
    entity.setType(type);
    entity.setDefinition(definition);
    entity.setColor(color);
    entity.setOrder(order);
    entity.setStixId(stixId);
    entity.setProtectedDefinition(false);
    entity.setTenant(new Tenant(tenantId));
    return entity;
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
