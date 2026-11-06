package io.openaev.service.marking;

import io.openaev.context.MarkingCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.rest.exception.ForbiddenException;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Guards against clearance escalation: you may not grant a marking you do not hold yourself.
 *
 * <p>Design decision Q7. Without this, marking is not a boundary at all — anyone able to manage a
 * group could put themselves in it, grant it {@code TLP:RED}, and read everything. The capability
 * to manage groups would silently become the capability to read every marked row.
 *
 * <p><b>Checked against the resolved clearance, not the raw grants.</b> {@link MarkingCtx} is
 * already expanded by {@code MarkingScopeResolver}, so a user holding {@code TLP:AMBER} may grant
 * {@code TLP:GREEN} — which is right: they can already read every {@code TLP:GREEN} row, so
 * granting it discloses nothing they could not have disclosed by other means. Checking raw grants
 * instead would forbid that and be merely annoying, not safer.
 *
 * <p>The first admin resolves to the whole tenant scale (see {@code MarkingScopeResolver}), so they
 * pass this check without needing a special case here.
 *
 * <p>Mirrors {@code PrivilegeEscalationValidator}, which does the same job for capabilities.
 */
public final class MarkingEscalationValidator {

  private MarkingEscalationValidator() {}

  /**
   * @param clearance the caller's own clearance in the tenant being written to
   * @param requested the markings they are trying to grant
   * @throws ForbiddenException naming the markings they do not hold
   */
  public static void assertCanAssignMarkings(
      MarkingCtx clearance, Collection<MarkingDefinition> requested) {
    Set<String> held =
        clearance instanceof MarkingCtx.Restricted restricted
            ? Set.copyOf(restricted.markingIds())
            : Set.of();

    List<String> unheld =
        requested.stream()
            .filter(marking -> !held.contains(marking.getId()))
            .map(MarkingDefinition::getDefinition)
            .sorted()
            .toList();

    if (!unheld.isEmpty()) {
      // Names rather than ids: the caller chose these in a UI that shows names, and an id would
      // make a legitimate mistake unreadable.
      throw new ForbiddenException(
          "Cannot assign markings you do not hold: " + String.join(", ", unheld));
    }
  }

  /**
   * Guards a definition's place on its scale: you may not define or update a marking above your own
   * clearance.
   *
   * @param held the definitions the caller's clearance covers in the tenant being written to
   * @param type the type of the definition being placed
   * @param order the order it is being placed at
   * @throws ForbiddenException when the order is above the caller's clearance on the type
   */
  public static void assertCanDefineMarking(
      Collection<MarkingDefinition> held, String type, int order) {
    OptionalInt highestHeld =
        held.stream()
            .filter(definition -> type.equals(definition.getType()))
            .mapToInt(MarkingDefinition::getOrder)
            .max();
    if (highestHeld.isEmpty() || order > highestHeld.getAsInt()) {
      throw new ForbiddenException("Cannot define a marking above your own clearance");
    }
  }
}
