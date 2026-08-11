package io.openaev.database.specification;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

// These specifications rely on correlated EXISTS subqueries instead of join + groupBy on purpose:
// with a groupBy, Spring Data's derived count query sums the per-group counts (it counts joined
// agent rows, not endpoints), which inflates pagination totals for endpoints with several primary
// agents (see https://github.com/spring-projects/spring-data-jpa/issues/2361 and #3208).
public class EndpointSpecification {

  private EndpointSpecification() {}

  public static Specification<Endpoint> findEndpointsForInjectionOrAgentlessEndpoints() {
    return findAgentlessEndpoints().or(findEndpointsForInjection());
  }

  /** Endpoints having at least one primary agent (agent without parent nor inject). */
  public static Specification<Endpoint> findEndpointsForInjection() {
    return (root, query, criteriaBuilder) -> {
      Subquery<String> agentSubquery = query.subquery(String.class);
      Root<Agent> agent = agentSubquery.from(Agent.class);
      agentSubquery
          .select(agent.get("id"))
          .where(
              criteriaBuilder.equal(agent.get("asset"), root),
              criteriaBuilder.isNull(agent.get("parent")),
              criteriaBuilder.isNull(agent.get("inject")));
      return criteriaBuilder.exists(agentSubquery);
    };
  }

  /** Endpoints without any agent. */
  public static Specification<Endpoint> findAgentlessEndpoints() {
    return (root, query, criteriaBuilder) -> {
      Subquery<String> agentSubquery = query.subquery(String.class);
      Root<Agent> agent = agentSubquery.from(Agent.class);
      agentSubquery.select(agent.get("id")).where(criteriaBuilder.equal(agent.get("asset"), root));
      return criteriaBuilder.not(criteriaBuilder.exists(agentSubquery));
    };
  }

  public static Specification<Endpoint> findEndpointsForAssetGroup(
      @NotNull final String assetGroupId) {
    return (root, query, criteriaBuilder) -> {
      Subquery<String> assetGroupSubquery = query.subquery(String.class);
      Root<AssetGroup> assetGroup = assetGroupSubquery.from(AssetGroup.class);
      Join<AssetGroup, Asset> assets = assetGroup.join("assets");
      assetGroupSubquery
          .select(assetGroup.get("id"))
          .where(
              criteriaBuilder.equal(assetGroup.get("id"), assetGroupId),
              criteriaBuilder.equal(assets.get("id"), root.get("id")));
      return criteriaBuilder.exists(assetGroupSubquery);
    };
  }

  public static Specification<Endpoint> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }

  public static Specification<Endpoint> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
