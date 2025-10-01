package io.openaev.service.targets.search.specifications;

import io.openaev.database.model.*;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchSpecificationUtils<T> {
  private final IncludeDirectEndpointTargetsSpecification<T>
      includeDirectEndpointTargetsSpecification;
  private final IncludeMembersOfAssetGroupsSpecification<T>
      includeMembersOfAssetGroupsSpecification;
  private final ExcludeMembersOfAssetGroupsSpecification<T>
      excludeMembersOfAssetGroupsSpecification;

  public record AssetGroupSplit(
      List<AssetGroup> includedAssetGroups,
      List<AssetGroup> excludedAssetGroups,
      Filters.FilterOperator filterOperator) {}

  public Specification<T> compileSpecificationForTags(
      SearchPaginationInput input, List<String> joinPath) {

    Filters.Filter tagsFilter = getTagsFilter(input);
    if (tagsFilter == null) {
      return null;
    }

    Filters.FilterGroup filterConfig = getFilterAsFilterGroup(input.getFilterGroup(), tagsFilter);
    Specification<Endpoint> spec = FilterUtilsJpa.computeFilterGroupJpa(filterConfig);

    return ((root, query, criteriaBuilder) -> {
      Subquery<Integer> subQuery = query.subquery(Integer.class);
      Root<Endpoint> assetTable = subQuery.from(Endpoint.class);
      From<?, ?> finalJoin =
          createJoinedFrom(
              assetTable, joinPath.stream().filter(path -> !"assets".equals(path)).toList());

      subQuery
          .select(criteriaBuilder.literal(1))
          .where(
              spec.toPredicate(assetTable, query, criteriaBuilder),
              criteriaBuilder.equal(
                  finalJoin.get("id"), query.getRoots().stream().findFirst().get().get("id")));
      return criteriaBuilder.exists(subQuery);
    });
  }

  private Filters.FilterGroup getFilterAsFilterGroup(
      Filters.FilterGroup filterGroup, Filters.Filter filter) {
    Filters.FilterGroup fg = new Filters.FilterGroup();
    fg.setMode(filterGroup.getMode());
    if (filter != null) {
      Filters.Filter newFilter = new Filters.Filter();
      newFilter.setOperator(filter.getOperator());
      newFilter.setKey("asset_tags");
      newFilter.setOperator(filter.getOperator());
      newFilter.setValues(filter.getValues());
      fg.setFilters(List.of(newFilter));
    } else {
      fg.setFilters(List.of());
    }
    return fg;
  }

  private Filters.Filter getTagsFilter(SearchPaginationInput input) {
    Filters.FilterGroup filterGroup = input.getFilterGroup();
    String key = "target_tags";

    return filterGroup.getFilters().stream()
        .filter(filter -> key.equals(filter.getKey()))
        .findFirst()
        .orElse(null);
  }

  public Specification<T> compileSpecificationForAssetGroupMembership(
      Inject scopedInject, SearchPaginationInput input, List<String> joinPath) {
    AssetGroupSplit split = determineAssetGroupSplit(scopedInject, input);
    return switch (split.filterOperator) {
      case null ->
          includeMembersOfAssetGroupsSpecification
              .buildSpecification(
                  split.includedAssetGroups.stream().map(AssetGroup::getId).toList(), joinPath)
              .or(
                  includeDirectEndpointTargetsSpecification.buildSpecification(
                      scopedInject, joinPath));

      case contains, not_empty ->
          includeMembersOfAssetGroupsSpecification.buildSpecification(
              split.includedAssetGroups.stream().map(AssetGroup::getId).toList(), joinPath);

      case empty ->
          excludeMembersOfAssetGroupsSpecification
              .buildSpecification(
                  split.excludedAssetGroups.stream().map(AssetGroup::getId).toList(), joinPath)
              .and(
                  includeDirectEndpointTargetsSpecification.buildSpecification(
                      scopedInject, joinPath));
      case not_contains ->
          includeMembersOfAssetGroupsSpecification
              .buildSpecification(
                  split.includedAssetGroups.stream().map(AssetGroup::getId).toList(), joinPath)
              .and(
                  excludeMembersOfAssetGroupsSpecification.buildSpecification(
                      split.excludedAssetGroups.stream().map(AssetGroup::getId).toList(), joinPath))
              .or(
                  includeDirectEndpointTargetsSpecification.buildSpecification(
                      scopedInject, joinPath));
      default -> throw new IllegalArgumentException();
    };
  }

  private AssetGroupSplit determineAssetGroupSplit(
      Inject scopedInject, SearchPaginationInput input) {
    List<AssetGroup> allTargetAssetGroups = scopedInject.getAssetGroups();

    Filters.Filter assetGroupFilter = getAssetGroupFilter(input);
    if (assetGroupFilter == null) {
      return new AssetGroupSplit(allTargetAssetGroups, List.of(), null);
    }

    return switch (assetGroupFilter.getOperator()) {
      case contains ->
          new AssetGroupSplit(
              allTargetAssetGroups.stream()
                  .filter(ag -> assetGroupFilter.getValues().contains(ag.getId()))
                  .toList(),
              List.of(),
              assetGroupFilter.getOperator());
      case not_empty ->
          new AssetGroupSplit(allTargetAssetGroups, List.of(), assetGroupFilter.getOperator());
      case empty ->
          new AssetGroupSplit(List.of(), allTargetAssetGroups, assetGroupFilter.getOperator());
      case not_contains ->
          new AssetGroupSplit(
              allTargetAssetGroups.stream()
                  .filter(ag -> !assetGroupFilter.getValues().contains(ag.getId()))
                  .toList(),
              allTargetAssetGroups.stream()
                  .filter(ag -> assetGroupFilter.getValues().contains(ag.getId()))
                  .toList(),
              assetGroupFilter.getOperator());
      default ->
          throw new IllegalArgumentException("Unknown operator: " + assetGroupFilter.getOperator());
    };
  }

  private Filters.Filter getAssetGroupFilter(SearchPaginationInput input) {
    Filters.FilterGroup filterGroup = input.getFilterGroup();
    String key = "target_asset_groups";

    return filterGroup.getFilters().stream()
        .filter(filter -> key.equals(filter.getKey()))
        .findFirst()
        .orElse(null);
  }

  // static
  public static Specification<Endpoint> compileFilterGroupsWithOR(
      @NotNull List<Filters.FilterGroup> filterGroups) {
    Specification<Endpoint> result = null;
    for (Filters.FilterGroup filterGroup : filterGroups) {
      Specification<Endpoint> converted = FilterUtilsJpa.computeFilterGroupJpa(filterGroup);
      if (result == null) {
        result = converted;
        continue;
      }
      result = result.or(converted);
    }
    return result;
  }

  public static From<?, ?> createJoinedFrom(From<?, ?> root, List<String> joinPath) {
    From<?, ?> finalJoin = root;
    for (String path : joinPath) {
      finalJoin = finalJoin.join(path);
    }
    return finalJoin;
  }
}
