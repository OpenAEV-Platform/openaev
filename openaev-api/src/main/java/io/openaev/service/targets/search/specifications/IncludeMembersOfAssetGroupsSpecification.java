package io.openaev.service.targets.search.specifications;

import static io.openaev.service.targets.search.specifications.SearchSpecificationUtils.compileFilterGroupsWithOR;
import static io.openaev.service.targets.search.specifications.SearchSpecificationUtils.createJoinedFrom;

import io.openaev.database.model.*;
import io.openaev.database.repository.AssetGroupRepository;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IncludeMembersOfAssetGroupsSpecification<T> {
  private final AssetGroupRepository assetGroupRepository;

  public Specification<T> buildSpecification(List<String> assetGroupIds, List<String> joinPath) {
    Specification<T> dynamicFiltersSpec = getDynamicFilterSpecification(assetGroupIds, joinPath);
    Specification<T> transitiveMembershipSpec =
        getTransitiveTargetingSpecification(assetGroupIds, joinPath);

    return dynamicFiltersSpec == null
        ? transitiveMembershipSpec
        : dynamicFiltersSpec.or(transitiveMembershipSpec);
  }

  private Specification<T> getDynamicFilterSpecification(
      List<String> assetGroupIds, List<String> joinPath) {
    Specification<Endpoint> positiveSpec =
        compileFilterGroupsWithOR(
            assetGroupRepository.rawDynamicFiltersByAssetGroupIds(assetGroupIds).stream()
                .map(df -> df.getAssetGroupDynamicFilter())
                .filter(fg -> !fg.getFilters().isEmpty())
                .toList());

    if (positiveSpec == null) {
      return null;
    }

    return ((root, query, criteriaBuilder) -> {
      Subquery<Integer> subQuery = query.subquery(Integer.class);
      Root<Endpoint> assetTable = subQuery.from(Endpoint.class);
      From<?, ?> finalJoin =
          createJoinedFrom(
              assetTable, joinPath.stream().filter(path -> !"assets".equals(path)).toList());

      subQuery
          .select(criteriaBuilder.literal(1))
          .where(
              positiveSpec.toPredicate(assetTable, query, criteriaBuilder),
              criteriaBuilder.equal(finalJoin.get("id"), root.get("id")));
      return criteriaBuilder.exists(subQuery);
    });
  }

  private Specification<T> getTransitiveTargetingSpecification(
      List<String> assetGroupIds, List<String> joinPath) {
    return (root, query, criteriaBuilder) -> {
      Subquery<Integer> subQuery = query.subquery(Integer.class);
      Root<AssetGroup> assetGroupTable = subQuery.from(AssetGroup.class);
      From<?, ?> finalJoin = createJoinedFrom(assetGroupTable, joinPath);

      subQuery
          .select(criteriaBuilder.literal(1))
          .where(
              criteriaBuilder.equal(finalJoin.get("id"), root.get("id")),
              assetGroupTable.get("id").in(assetGroupIds));
      return criteriaBuilder.exists(subQuery);
    };
  }
}
