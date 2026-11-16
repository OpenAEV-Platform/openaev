package io.openaev.database.specification;

import io.openaev.context.MarkingCtx;
import io.openaev.database.model.MarkingDefinition;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public class MarkingDefinitionSpecification {

  private MarkingDefinitionSpecification() {}

  /**
   * Gives a specification to find marking definitions based on tenants and correct clearance of the
   * caller.
   *
   * @param tenantIds the tenants identifiers to restrict the search
   * @param clearance the clearance to resolve through
   * @return empty when the clearance grants nothing: the caller short-circuits to an empty result
   *     instead of querying.
   */
  public static Optional<Specification<MarkingDefinition>> tenantAndClearanceSpecification(
      Set<String> tenantIds, MarkingCtx clearance) {
    return switch (clearance) {
      case MarkingCtx.None ignored -> Optional.empty();
      case MarkingCtx.Restricted restricted ->
          Optional.of(
              tenantSpecification(tenantIds).and(idsSpecification(restricted.markingIds())));
      // An unresolved background-only intention that neither MarkingScopeSupplier#clearanceFor nor
      // MarkingClearanceCacheManager#findClearance returns on the HTTP path: handled as "sees
      // everything" to keep the switch exhaustive.
      case MarkingCtx.All ignored -> Optional.of(tenantSpecification(tenantIds));
    };
  }

  public static Specification<MarkingDefinition> tenantSpecification(Set<String> tenantIds) {
    return (root, query, criteriaBuilder) -> root.get("tenant").get("id").in(tenantIds);
  }

  public static Specification<MarkingDefinition> idsSpecification(Collection<String> ids) {
    return (root, query, criteriaBuilder) -> root.get("id").in(ids);
  }

  public static Specification<MarkingDefinition> typeSpecification(String type) {
    return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type"), type);
  }
}
