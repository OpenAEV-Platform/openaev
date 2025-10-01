package io.openaev.database.specification;

import io.openaev.database.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
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
   * Specification to filter InjectorContracts based on user grants on associated payloads. Injector
   * contracts without payload are always accessible. Injector contracts with payload are only
   * accessible if the user has at least OBSERVER grant on the payload OR the ACCESS capability on
   * payloads. Usage:
   * myResourceSearchSpecification.and(SpecificationUtils.hasAccessToInjectorContract(userId,
   * isAdmin, hasCapaForClass)) Only works for InjectorContract entities, return cb.conjunction()
   * otherwise.
   *
   * @param currentUser current user performing the search
   * @return Specification for filtering InjectorContracts based on user grants
   */
  public static Specification<InjectorContract> hasAccessToInjectorContract(
      final User currentUser) {
    return (root, query, cb) -> {
      // If user bypasses grants entirely or has the specific capa for payloads, return all
      if (currentUser.isAdminOrBypass()
          || currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS)) {
        return cb.conjunction();
      }

      // Join payload (can be null because of left join)
      Join<InjectorContract, Payload> payloadJoin = root.join("payload", JoinType.LEFT);

      // Case 1: no payload
      Predicate noPayload = cb.isNull(payloadJoin.get("id"));

      // Case 2: payload accessible
      Predicate payloadGranted =
          payloadJoin
              .get("id")
              .in(
                  SpecificationUtils.accessibleResourcesSubquery(
                      query,
                      cb,
                      currentUser.getId(),
                      Grant.GRANT_RESOURCE_TYPE.PAYLOAD,
                      Grant.GRANT_TYPE.OBSERVER.andHigher()));

      return cb.or(noPayload, payloadGranted);
    };
  }
}
