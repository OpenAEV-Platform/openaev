package io.openaev.service.marking_definition;

import java.util.Locale;

/** What creating a marking definition by hand and importing one have in common. */
final class MarkingDefinitionNormalizer {

  private MarkingDefinitionNormalizer() {}

  /**
   * Canonicalizes to upper-case so {@code type} is never compared case-sensitively downstream:
   * {@link io.openaev.config.MarkingScopeResolver} groups a caller's clearance by {@code type} in a
   * plain {@code HashMap}.
   */
  static String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }
}
