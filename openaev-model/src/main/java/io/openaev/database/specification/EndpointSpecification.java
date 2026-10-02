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

public class EndpointSpecification {

  private EndpointSpecification() {}

  public static Specification<Endpoint> findEndpointsForInjectionOrAgentlessEndpoints() {
    return distinctEndpoints().and(findAgentlessEndpoints().or(findEndpointsForInjection()));
  }

  /**
   * Marks the query as distinct. The specifications below never duplicate rows on their own (they
   * rely on EXISTS), but user filters resolved by {@code FilterUtilsJpa} can traverse to-many paths
   * (e.g. {@code assetGroups.id}) and produce an implicit duplicating join. {@code DISTINCT} keeps
   * both the page content and the count correct: Spring Data reads {@code query.isDistinct()} to
   * emit {@code count(distinct endpoint)} instead of {@code count(endpoint)}.
   *
   * <p>Do NOT replace this with a {@code GROUP BY}: Spring Data cannot handle a grouped count query
   * (it sums the returned rows), which is exactly what made the endpoint total unstable across
   * pages (see spring-projects/spring-data-jpa#2376).
   */
  private static Specification<Endpoint> distinctEndpoints() {
    return (root, query, criteriaBuilder) -> {
      query.distinct(true);
      return criteriaBuilder.conjunction();
    };
  }

  /**
   * Endpoints having at least one "injectable" agent (no parent, not bound to an inject).
   *
   * <p>Implemented with an {@code EXISTS} subquery on purpose: a {@code JOIN} would duplicate rows
   * and require a {@code GROUP BY} to deduplicate them. Spring Data applies the specification to
   * the count query as well, and it cannot handle a {@code GROUP BY} there: the count returns one
   * row per group and Spring sums them, so the total ends up counting agents instead of endpoints
   * (see spring-projects/spring-data-jpa#2376). {@code EXISTS} produces no duplicates, so no
   * grouping is needed and the count stays correct on every page.
   */
  public static Specification<Endpoint> findEndpointsForInjection() {
    return (root, query, criteriaBuilder) -> {
      Subquery<String> subquery = query.subquery(String.class);
      Root<Agent> agent = subquery.from(Agent.class);
      subquery
          .select(agent.get("id"))
          .where(
              criteriaBuilder.equal(agent.get("asset"), root),
              criteriaBuilder.isNull(agent.get("parent")),
              criteriaBuilder.isNull(agent.get("inject")));
      return criteriaBuilder.exists(subquery);
    };
  }

  public static Specification<Endpoint> findAgentlessEndpoints() {
    // No join here: isEmpty() already compiles to a NOT EXISTS, so no grouping is required.
    return (root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get("agents"));
  }

  /**
   * Endpoints statically attached to the given asset group. Uses an {@code EXISTS} subquery for the
   * same reason as {@link #findEndpointsForInjection()}.
   */
  public static Specification<Endpoint> findEndpointsForAssetGroup(
      @NotNull final String assetGroupId) {
    return (root, query, criteriaBuilder) -> {
      Subquery<String> subquery = query.subquery(String.class);
      Root<AssetGroup> assetGroup = subquery.from(AssetGroup.class);
      Join<AssetGroup, Asset> assets = assetGroup.join("assets");
      subquery
          .select(assetGroup.get("id"))
          .where(
              criteriaBuilder.equal(assetGroup.get("id"), assetGroupId),
              criteriaBuilder.equal(assets, root));
      return criteriaBuilder.exists(subquery);
    };
  }

  public static Specification<Endpoint> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }

  public static Specification<Endpoint> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
