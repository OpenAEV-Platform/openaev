package io.openaev.database.specification;

import io.openaev.database.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public class InjectorContractSpecification {

  private InjectorContractSpecification() {}

  public static Specification<InjectorContract> fromAttackPattern(String attackPatternId) {
    return (root, query, cb) -> cb.equal(root.get("attackPatterns").get("id"), attackPatternId);
  }

  public static Specification<InjectorContract> byPayloadId(final String payloadId) {
    if (payloadId == null || payloadId.isEmpty()) {
      throw new IllegalArgumentException("Payload ID must not be null or empty");
    }
    return (root, query, cb) -> {
      Join<Object, Object> payload = root.join("payload", JoinType.LEFT);
      return cb.equal(payload.get("id"), payloadId);
    };
  }

  /**
   * Keeps one copy of each contract id: the copy in the lowest tenant id of the request's scope. A
   * built-in contract id repeats in every tenant that registers its injector, so a scope of several
   * tenants reads the same id once per tenant; with this specification a search, its total and its
   * facet counts read it once. The other copies are looked up through the same scope, so a scope of
   * one tenant keeps every row.
   */
  public static Specification<InjectorContract> onePerContractId() {
    return (root, query, cb) -> {
      Subquery<Integer> lowerCopy = query.subquery(Integer.class);
      Root<InjectorContract> other = lowerCopy.from(InjectorContract.class);
      lowerCopy
          .select(cb.literal(1))
          .where(
              cb.equal(other.get("compositeId").get("id"), root.get("compositeId").get("id")),
              cb.lessThan(
                  other.get("compositeId").<String>get("tenantId"),
                  root.get("compositeId").<String>get("tenantId")));
      return cb.not(cb.exists(lowerCopy));
    };
  }

  /**
   * Specification to filter InjectorContracts based on user grants. Only injector contracts on
   * which the user has at least an OBSERVER grant (grant_resource = injector_contract_id,
   * grant_resource_type = THREAT_ARSENAL) or the ACCESS_THREAT_ARSENALS capability are returned.
   *
   * @param currentUser current user performing the search
   * @return Specification for filtering InjectorContracts based on user grants
   */
  public static Specification<InjectorContract> hasAccessToInjectorContract(
      final User currentUser) {
    return (root, query, cb) -> {
      if (currentUser.isAdminOrBypass()
          || currentUser.getCapabilities().contains(Capability.ACCESS_THREAT_ARSENALS)) {
        return cb.conjunction();
      }

      Subquery<String> granted =
          SpecificationUtils.accessibleResourcesSubquery(
              query,
              cb,
              currentUser.getId(),
              currentUser.scopedTenantId(),
              Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL,
              Grant.GRANT_TYPE.OBSERVER.andHigher());
      return cb.or(
          root.get("compositeId").get("id").in(granted),
          root.join("payload", JoinType.LEFT).get("identity").get("id").in(granted));
    };
  }
}
