package io.openaev.database.specification;

import io.openaev.database.model.*;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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
   * Keeps one copy of each contract id: the best-ranked copy among those that match {@code
   * matching}. A built-in contract id repeats in every tenant that registers its injector, so a
   * scope of several tenants reads the same id once per tenant; with this specification a search,
   * its total and its facet counts read it once.
   *
   * <p>The ranking is the one of the contract GET with no selector, {@link CopyRanking}: the
   * default tenant's copy when the scope holds it, else the copy of the lowest tenant id. The other
   * copies are looked up through the same scope, so a scope of one tenant keeps every row. A copy
   * that {@code matching} refuses never outranks one it accepts: the contract is listed through the
   * best copy that satisfies the request's filter and text search.
   *
   * @param matching the filter and text search of the request, applied to the competing copies;
   *     null when the request has none
   */
  public static Specification<InjectorContract> onePerContractId(
      @Nullable final Specification<InjectorContract> matching) {
    return (root, query, cb) -> {
      Subquery<Integer> betterCopy = query.subquery(Integer.class);
      Root<InjectorContract> other = betterCopy.from(InjectorContract.class);
      Path<String> otherTenant = other.get("compositeId").<String>get("tenantId");
      Path<String> rootTenant = root.get("compositeId").<String>get("tenantId");
      Predicate outranks =
          cb.and(
              cb.notEqual(otherTenant, rootTenant),
              cb.or(
                  cb.equal(otherTenant, Tenant.DEFAULT_TENANT_UUID),
                  cb.and(
                      cb.notEqual(rootTenant, Tenant.DEFAULT_TENANT_UUID),
                      cb.lessThan(otherTenant, rootTenant))));
      Predicate otherMatches = matching == null ? null : matching.toPredicate(other, query, cb);
      Predicate sameId =
          cb.equal(other.get("compositeId").get("id"), root.get("compositeId").get("id"));
      betterCopy
          .select(cb.literal(1))
          .where(
              otherMatches == null
                  ? cb.and(sameId, outranks)
                  : cb.and(sameId, outranks, otherMatches));
      return cb.not(cb.exists(betterCopy));
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
