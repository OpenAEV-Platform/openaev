package io.openaev.config;

import io.openaev.context.MarkingCtx;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Turns the markings a caller was <i>granted</i> into the clearance they <i>hold</i>.
 *
 * <p>This is the design trick that keeps the marking dimension as cheap as the tenant one (§2.2 of
 * the tech design). Marking is an <b>ordinal</b> check — {@code TLP:AMBER} implies {@code
 * TLP:GREEN} implies {@code TLP:CLEAR} — while tenant is a flat <b>set-membership</b> check. Rather
 * than teach the SQL rewrite two comparison styles, the ordinality is collapsed <b>here, in
 * Java</b>, once per request: take the highest order granted <i>per type</i>, then expand it back
 * into every id of that type at or below it. What reaches the database is a flat set, so the
 * predicate stays a plain containment test.
 *
 * <p><b>Per type, independently.</b> Types are separate scales, so holding {@code TLP:RED} says
 * nothing about {@code PAP}. A type the caller was granted nothing on contributes nothing — it does
 * not silently grant that type's lowest level.
 *
 * <p>Pure function, no I/O: the rows come from {@code MarkingClearanceCacheManager}, which is the
 * piece that must not open a Hibernate session. Deliberately mirrors {@link TenantScopeResolver}.
 */
@Component
public class MarkingScopeResolver {

  /**
   * A marking definition reduced to what ordinality resolution needs.
   *
   * @param id the marking id, as stored in a row's {@code marking_ids}
   * @param type the scale it belongs to (TLP, PAP, a custom one)
   * @param order its rank within that scale; higher sees more
   */
  public record MarkingRef(String id, String type, int order) {
    public MarkingRef {
      Objects.requireNonNull(id, "marking id must not be null");
      Objects.requireNonNull(type, "marking type must not be null");
    }
  }

  /**
   * @param grantedIds the marking ids the caller's groups grant, possibly empty (never null)
   * @param tenantDefinitions every marking defined in the tenant in scope (never null)
   * @param bypass whether the caller is admin or holds BYPASS
   * @return the clearance to run the transaction under
   */
  public MarkingCtx resolve(
      Collection<String> grantedIds, Collection<MarkingRef> tenantDefinitions, boolean bypass) {
    Objects.requireNonNull(grantedIds, "grantedIds must not be null");
    Objects.requireNonNull(tenantDefinitions, "tenantDefinitions must not be null");

    // A bypassing caller holds the whole tenant scale, whatever their groups say. Resolved into an
    // explicit list here rather than passed on as MarkingCtx.all(): that intention belongs to the
    // background primitive, and letting it reach the HTTP path would put a wildcard in the channel.
    if (bypass) {
      return MarkingCtx.forMarkings(sortedIds(tenantDefinitions, d -> true));
    }

    Map<String, Integer> highestOrderPerType = new HashMap<>();
    for (MarkingRef definition : tenantDefinitions) {
      if (grantedIds.contains(definition.id())) {
        highestOrderPerType.merge(definition.type(), definition.order(), Math::max);
      }
    }
    if (highestOrderPerType.isEmpty()) {
      return MarkingCtx.none();
    }

    return MarkingCtx.forMarkings(
        sortedIds(
            tenantDefinitions,
            definition -> {
              Integer highest = highestOrderPerType.get(definition.type());
              return highest != null && definition.order() <= highest;
            }));
  }

  /** Sorted so the GUC value is deterministic, as {@link TenantScopeResolver} does for tenants. */
  private static TreeSet<String> sortedIds(
      Collection<MarkingRef> definitions, java.util.function.Predicate<MarkingRef> keep) {
    TreeSet<String> ids = new TreeSet<>();
    for (MarkingRef definition : definitions) {
      if (keep.test(definition)) {
        ids.add(definition.id());
      }
    }
    return ids;
  }
}
