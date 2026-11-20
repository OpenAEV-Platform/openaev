package io.openaev.service.marking_definition;

import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import java.util.Locale;

/** What creating a marking definition by hand and importing one have in common. */
final class MarkingDefinitionFactory {

  private MarkingDefinitionFactory() {}

  /**
   * Canonicalizes to upper-case so {@code type} is never compared case-sensitively downstream:
   * {@link io.openaev.config.MarkingScopeResolver} groups a caller's clearance by {@code type} in a
   * plain {@code HashMap}.
   */
  static String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  static MarkingDefinition buildEntity(
      String tenantId, String type, String definition, String color, int order) {
    MarkingDefinition entity = new MarkingDefinition();
    entity.setType(type);
    entity.setDefinition(definition);
    entity.setColor(color);
    entity.setOrder(order);
    entity.setProtectedDefinition(false);
    entity.setTenant(new Tenant(tenantId));
    return entity;
  }
}
