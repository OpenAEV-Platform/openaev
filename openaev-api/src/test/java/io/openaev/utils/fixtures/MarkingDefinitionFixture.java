package io.openaev.utils.fixtures;

import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.database.model.MarkingDefinition;
import java.time.Instant;
import java.util.UUID;

/**
 * Test data for {@link MarkingDefinition}.
 *
 * <p>Every generated definition is unique. This is not cosmetic: the migration seeds nine default
 * markings (TLP:CLEAR..TLP:RED, PAP:CLEAR..PAP:RED) for EVERY tenant, so a fixture reusing one of
 * those definitions would collide with seeded ground truth. Unique definitions also keep assertions
 * robust to the seed — filter by a fixture-specific definition instead of counting rows.
 */
public class MarkingDefinitionFixture {

  public static final String DEFAULT_COLOR = "#c62828";
  public static final String ALTERNATE_COLOR = "#2e7d32";
  public static final String INVALID_COLOR = "not-a-colour";

  /** Above the seeded defaults' 10..50 band, so fixtures never tie with a seeded order. */
  public static final int DEFAULT_ORDER = 60;

  private MarkingDefinitionFixture() {}

  /** A definition that cannot collide with the nine per-tenant defaults. */
  public static String uniqueDefinition() {
    return "MARKING:" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  /** A shared, unique token usable as a textSearch needle across a group of fixtures. */
  public static String uniqueSearchToken() {
    return "SEARCHTOKEN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  // -- ENTITIES (tenant attribution is the composer's job: v2 stamps no tenant automatically) --

  public static MarkingDefinition createDefaultMarkingDefinition() {
    return createMarkingDefinition(
        MarkingDefinition.TYPE_TLP, uniqueDefinition(), DEFAULT_ORDER, DEFAULT_COLOR);
  }

  public static MarkingDefinition createMarkingDefinitionWithDefinition(String definition) {
    return createMarkingDefinition(
        MarkingDefinition.TYPE_TLP, definition, DEFAULT_ORDER, DEFAULT_COLOR);
  }

  public static MarkingDefinition createMarkingDefinition(
      String type, String definition, int order, String color) {
    MarkingDefinition marking = new MarkingDefinition();
    marking.setType(type);
    marking.setDefinition(definition);
    marking.setOrder(order);
    marking.setColor(color);
    return marking;
  }

  /**
   * Same as {@link #createMarkingDefinition(String, String, int, String)} with an explicit,
   * deterministic {@code createdAt}/{@code updatedAt} for tests asserting creation-time ordering.
   */
  public static MarkingDefinition createMarkingDefinition(
      String type, String definition, int order, String color, Instant createdAt) {
    MarkingDefinition marking = createMarkingDefinition(type, definition, order, color);
    marking.setCreatedAt(createdAt);
    marking.setUpdatedAt(createdAt);
    return marking;
  }

  /** A protected (platform-seeded-like) definition, which the API refuses to update or delete. */
  public static MarkingDefinition createProtectedMarkingDefinition(
      String type, String definition, int order, String color) {
    MarkingDefinition marking = createMarkingDefinition(type, definition, order, color);
    marking.setProtectedDefinition(true);
    return marking;
  }

  // -- INPUTS --

  public static MarkingDefinitionInput createDefaultInput() {
    return createInput(
        MarkingDefinition.TYPE_TLP, uniqueDefinition(), DEFAULT_ORDER, DEFAULT_COLOR);
  }

  public static MarkingDefinitionInput createInputWithDefinition(String definition) {
    return createInput(MarkingDefinition.TYPE_TLP, definition, DEFAULT_ORDER, DEFAULT_COLOR);
  }

  public static MarkingDefinitionInput createInputWithColor(String color) {
    return createInput(MarkingDefinition.TYPE_TLP, uniqueDefinition(), DEFAULT_ORDER, color);
  }

  public static MarkingDefinitionInput createInput(
      String type, String definition, int order, String color) {
    return new MarkingDefinitionInput(type, definition, color, order);
  }

  /** Round-trips a persisted entity into an update payload. */
  public static MarkingDefinitionInput toInput(MarkingDefinition marking) {
    return new MarkingDefinitionInput(
        marking.getType(), marking.getDefinition(), marking.getColor(), marking.getOrder());
  }
}
