package io.openaev.database.specification;

import io.openaev.database.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
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

  /** Contracts without a payload, or whose payload the tenant did not uninstall (F26). */
  public static Specification<InjectorContract> withoutUninstalledPayload() {
    return (root, query, cb) -> {
      Join<Object, Object> payload = root.join("payload", JoinType.LEFT);
      Join<Object, Object> identity = payload.join("identity", JoinType.LEFT);
      return cb.or(cb.isNull(payload.get("id")), cb.isFalse(identity.get("uninstalled")));
    };
  }

  /**
   * Contracts without payload, or whose payload is the default version of an installed one. A
   * variant whose versions are all revoked keeps its newest one listed, so it can be resolved.
   */
  public static Specification<InjectorContract> withLatestPayload() {
    return (root, query, cb) -> {
      Join<Object, Object> payload = root.join("payload", JoinType.LEFT);
      Join<Object, Object> identity = payload.join("identity", JoinType.LEFT);
      Subquery<Integer> newer = query.subquery(Integer.class);
      Root<Payload> other = newer.from(Payload.class);
      newer
          .select(cb.literal(1))
          .where(
              cb.equal(other.get("identity"), identity),
              cb.greaterThan(other.<Instant>get("createdAt"), payload.<Instant>get("createdAt")));
      return cb.or(
          cb.isNull(payload.get("id")),
          cb.and(
              cb.isFalse(identity.get("uninstalled")),
              cb.or(
                  cb.equal(payload.get("id"), identity.get("latestPayloadId")),
                  cb.and(
                      cb.isNull(identity.get("latestPayloadId")),
                      cb.isNotNull(identity.get("overrides")),
                      cb.not(cb.exists(newer))))));
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
