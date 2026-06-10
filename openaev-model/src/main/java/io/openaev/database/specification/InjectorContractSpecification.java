package io.openaev.database.specification;

import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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
  public static Specification<InjectorContract> hasAccessToInjectorContract(TxCtx ctx,
                                                                            final User currentUser) {
    return (root, query, cb) -> {
      if (currentUser.isAdminOrBypass(ctx.tenantIdFromUri())
          || currentUser.getCapabilities(ctx.tenantIdFromUri()).contains(Capability.ACCESS_THREAT_ARSENALS)) {
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
