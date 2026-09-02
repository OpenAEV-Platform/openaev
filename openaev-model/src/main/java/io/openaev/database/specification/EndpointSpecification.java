package io.openaev.database.specification;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class EndpointSpecification {

  private EndpointSpecification() {}

  public static Specification<Endpoint> findEndpointsForInjectionOrAgentlessEndpoints() {
    return findAgentlessEndpoints().or(findEndpointsForInjection());
  }

  public static Specification<Endpoint> findEndpointsForInjection() {
    return (root, query, criteriaBuilder) -> {
      Join<Endpoint, Agent> agentsJoin = root.join("agents", JoinType.LEFT);
      // De-duplicates the rows multiplied by the LEFT JOIN. Deliberately DISTINCT and not
      // GROUP BY(id): a scope-filtered table is rewritten into an inline view, which carries no
      // primary key, so PostgreSQL can no longer infer the functional dependency that makes
      // "GROUP BY id" with a full projection legal.
      query.distinct(true);
      return criteriaBuilder.and(
          criteriaBuilder.isNull(agentsJoin.get("parent")),
          criteriaBuilder.isNull(agentsJoin.get("inject")));
    };
  }

  public static Specification<Endpoint> findAgentlessEndpoints() {
    return (root, query, criteriaBuilder) -> {
      // No join here (isEmpty is a subquery), so no rows are multiplied and no de-duplication is
      // needed at all.
      return criteriaBuilder.and(criteriaBuilder.isEmpty(root.get("agents")));
    };
  }

  public static Specification<Endpoint> findEndpointsForAssetGroup(
      @NotNull final String assetGroupId) {
    return (root, query, criteriaBuilder) -> {
      Join<Endpoint, AssetGroup> assetGroupJoin = root.join("assetGroups", JoinType.LEFT);
      query.distinct(true);
      return criteriaBuilder.and(criteriaBuilder.equal(assetGroupJoin.get("id"), assetGroupId));
    };
  }

  public static Specification<Endpoint> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }

  public static Specification<Endpoint> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
