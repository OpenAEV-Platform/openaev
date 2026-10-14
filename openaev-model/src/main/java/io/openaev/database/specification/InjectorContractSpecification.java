package io.openaev.database.specification;

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

  /** Contracts without payload, or whose payload is the latest non-revoked version. */
  public static Specification<InjectorContract> withLatestPayload() {
    return (root, query, cb) -> {
      Join<Object, Object> payload = root.join("payload", JoinType.LEFT);
      return cb.or(cb.isNull(payload.get("id")), cb.isTrue(payload.get("latest")));
    };
  }

  /** Contracts without payload, or whose payload is not revoked. */
  public static Specification<InjectorContract> withoutRevokedPayload() {
    return (root, query, cb) -> {
      Join<Object, Object> payload = root.join("payload", JoinType.LEFT);
      return cb.or(
          cb.isNull(payload.get("id")),
          cb.notEqual(payload.get("status"), Payload.PAYLOAD_STATUS.REVOKED));
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
