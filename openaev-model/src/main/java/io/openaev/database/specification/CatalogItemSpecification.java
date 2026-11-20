package io.openaev.database.specification;

import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.model.scenario.ScenarioIdentity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/** The catalogue items an Available tab lists: the offered version of each, not installed. */
public class CatalogItemSpecification {

  private CatalogItemSpecification() {}

  public static Specification<CatalogItem> latestOffered(
      String tenantId, Class<? extends CatalogItem> type) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("tenant").get("id"), tenantId),
            cb.equal(root.type(), type),
            cb.isTrue(root.get("latestOffered")));
  }

  /** No payload of the tenant shows a version of the item. */
  public static Specification<CatalogItem> payloadNotInstalled(String tenantId) {
    return (root, query, cb) -> {
      Subquery<Integer> installed = query.subquery(Integer.class);
      Root<PayloadIdentity> identity = installed.from(PayloadIdentity.class);
      installed
          .select(cb.literal(1))
          .where(
              sameItem(cb, identity, tenantId, root), cb.isNotNull(identity.get("latestVersion")));
      return cb.not(cb.exists(installed));
    };
  }

  /** No scenario of the tenant comes from the item. */
  public static Specification<CatalogItem> scenarioNotInstalled(String tenantId) {
    return (root, query, cb) -> {
      Subquery<Integer> installed = query.subquery(Integer.class);
      Root<ScenarioIdentity> identity = installed.from(ScenarioIdentity.class);
      Subquery<Integer> scenarios = installed.subquery(Integer.class);
      Root<Scenario> scenario = scenarios.from(Scenario.class);
      scenarios.select(cb.literal(1)).where(cb.equal(scenario.get("catalogIdentity"), identity));
      installed
          .select(cb.literal(1))
          .where(sameItem(cb, identity, tenantId, root), cb.exists(scenarios));
      return cb.not(cb.exists(installed));
    };
  }

  // The identity of the item in the tenant: its repository and external ID.
  private static Predicate sameItem(
      CriteriaBuilder cb, Root<?> identity, String tenantId, Root<CatalogItem> item) {
    return cb.and(
        cb.equal(identity.get("tenant").get("id"), tenantId),
        cb.equal(identity.get("repository"), item.get("release").get("repository")),
        cb.equal(identity.get("externalId"), item.get("externalId")));
  }
}
