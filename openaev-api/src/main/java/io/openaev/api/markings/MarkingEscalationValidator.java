package io.openaev.api.markings;

import io.openaev.context.MarkingCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.rest.exception.ForbiddenException;
import java.util.Collection;
import java.util.List;
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
 * <p>A bypassing caller resolves to the whole tenant scale (see {@code MarkingScopeResolver}), so
 * they pass this check without needing a special case here.
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
            .map(MarkingDefinition::getName)
            .sorted()
            .toList();

    if (!unheld.isEmpty()) {
      // Names rather than ids: the caller chose these in a UI that shows names, and an id would
      // make a legitimate mistake unreadable.
      throw new ForbiddenException(
          "Cannot assign markings you do not hold: " + String.join(", ", unheld));
    }
  }
}
