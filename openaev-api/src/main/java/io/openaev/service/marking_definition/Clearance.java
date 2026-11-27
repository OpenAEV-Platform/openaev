package io.openaev.service.marking_definition;

import io.openaev.database.model.MarkingDefinition;
import io.openaev.service.marking.MarkingEscalationValidator;
import java.util.List;
import java.util.OptionalInt;

/**
 * What a caller may place in a tenant: the markings its clearance covers, or every level.
 *
 * <p>Every level is not a set of today's definitions: the first admin also holds the orders above a
 * type's current top, and a type the tenant does not have yet.
 *
 * @param held the markings the caller's clearance covers, empty when {@code unlimited}
 * @param unlimited whether the caller holds every level
 */
record Clearance(List<MarkingDefinition> held, boolean unlimited) {

  static Clearance everyLevel() {
    return new Clearance(List.of(), true);
  }

  static Clearance of(List<MarkingDefinition> held) {
    return new Clearance(held, false);
  }

  boolean allows(String type, int order) {
    return unlimited || MarkingEscalationValidator.isWithinClearance(held, type, order);
  }

  /**
   * @throws io.openaev.rest.exception.MarkingAboveClearanceException when the order is above the
   *     clearance on the type
   */
  void assertAllows(String type, int order) {
    if (!unlimited) {
      MarkingEscalationValidator.assertCanDefineMarking(held, type, order);
    }
  }

  /** The highest order held on the type, empty when none of it is held. */
  OptionalInt highestHeldOrder(String type) {
    return MarkingEscalationValidator.highestHeldOrder(held, type);
  }
}
