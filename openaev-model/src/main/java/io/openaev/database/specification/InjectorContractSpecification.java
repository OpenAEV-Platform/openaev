package io.openaev.database.specification;

import io.openaev.database.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public class InjectorContractSpecification {

  private InjectorContractSpecification() {}

  /**
   * Keeps only contracts that have an injector registered in the same tenant as the contract.
   *
   * <p>The inject-creation picker must never offer a contract whose injector is not registered in
   * the current tenant: the inject can be created but dies at execution with a cryptic {@code
   * Injector not found}, because {@link io.openaev.executors.Executor} resolves the injector strictly
   * per tenant. The join row of {@link InjectorInjectorContract} always shares the {@code tenant_id}
   * of both endpoints (foreign-tenant links are rejected at link time), so matching the link on both
   * the contract id and the contract tenant id is enough and does not depend on the Hibernate {@code
   * tenantFilter}. This deliberately does not touch the Threat Arsenal catalog, which shares the
   * underlying query but is browsed, not executed.
   */
  public static Specification<InjectorContract> hasRegisteredInjector() {
    return (root, query, cb) -> {
      Subquery<Integer> sub = query.subquery(Integer.class);
      Root<InjectorInjectorContract> link = sub.from(InjectorInjectorContract.class);
      sub.select(cb.literal(1));
      sub.where(
          cb.equal(
              link.get("injectorContractId"),
              root.get(InjectorContract.COMPOSITE_ID_FIELD_NAME)
                  .get(InjectorContract.ID_FIELD_NAME)),
          cb.equal(
              link.get("tenantId"),
              root.get(InjectorContract.COMPOSITE_ID_FIELD_NAME).get("tenantId")));
      return cb.exists(sub);
    };
  }

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

  public static Specification<InjectorContract> byPayloadExternalId(
      final String payloadExternalId) {
    if (payloadExternalId == null || payloadExternalId.isEmpty()) {
      throw new IllegalArgumentException("Payload external ID must not be null or empty");
    }
    return (root, query, cb) -> {
      Join<Object, Object> payload = root.join("payload", JoinType.LEFT);
      return cb.equal(payload.get("externalId"), payloadExternalId);
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

      return root.get("compositeId")
          .get("id")
          .in(
              SpecificationUtils.accessibleResourcesSubquery(
                  query,
                  cb,
                  currentUser.getId(),
                  Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL,
                  Grant.GRANT_TYPE.OBSERVER.andHigher()));
    };
  }
}
