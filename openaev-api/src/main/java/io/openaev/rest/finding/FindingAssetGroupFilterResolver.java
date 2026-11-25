package io.openaev.rest.finding;

import io.openaev.database.model.Filters;
import io.openaev.database.model.Filters.Filter;
import io.openaev.database.model.Filters.FilterGroup;
import io.openaev.database.model.Filters.FilterMode;
import io.openaev.database.model.Filters.FilterOperator;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rewrites a {@code finding_asset_groups} filter of a findings search into a {@code finding_assets}
 * filter on the members of the requested groups, static and dynamic.
 *
 * <p>The {@code finding_asset_groups} attribute is declared on the JPA path {@code
 * assets.assetGroups.id}, the static membership table: a finding on an asset that only matches a
 * dynamic group's filter was counted for that group on the finding detail and in the search index
 * (both read {@link io.openaev.database.model.AssetGroupMembershipResolver}) but filtered out of
 * the search (#9850). Resolving the group ids to member asset ids here makes the search agree.
 */
@Component
@RequiredArgsConstructor
public class FindingAssetGroupFilterResolver {

  static final String ASSET_GROUPS_KEY = "finding_asset_groups";
  static final String ASSETS_KEY = "finding_assets";

  /** Matches no asset: a group with no member must filter every finding out, not none. */
  static final String NO_MEMBER = "__no_asset_group_member__";

  private final AssetGroupService assetGroupService;

  /**
   * The same input with each {@code eq} / {@code not_eq} filter on {@code finding_asset_groups}
   * replaced by a filter on {@code finding_assets}; the input itself is left unchanged. Other
   * operators ({@code empty}, {@code not_empty}) keep the static path.
   */
  public SearchPaginationInput resolve(final SearchPaginationInput input) {
    if (input == null || Filters.isEmptyFilterGroup(input.getFilterGroup())) {
      return input;
    }
    FilterGroup group = input.getFilterGroup();
    if (group.getFilters().stream().noneMatch(FindingAssetGroupFilterResolver::isResolvable)) {
      return input;
    }
    List<Filter> filters = new ArrayList<>();
    for (Filter filter : group.getFilters()) {
      if (isResolvable(filter)) {
        filters.addAll(resolve(filter, group.getMode()));
      } else {
        filters.add(filter);
      }
    }
    FilterGroup resolved = new FilterGroup(group.getMode(), filters);
    return SearchPaginationInput.builder()
        .page(input.getPage())
        .size(input.getSize())
        .filterGroup(resolved)
        .textSearch(input.getTextSearch())
        .sorts(input.getSorts())
        .build();
  }

  private static boolean isResolvable(final Filter filter) {
    return filter != null
        && ASSET_GROUPS_KEY.equals(filter.getKey())
        && filter.getValues() != null
        && !filter.getValues().isEmpty()
        && (filter.getOperator() == FilterOperator.eq
            || filter.getOperator() == FilterOperator.not_eq);
  }

  /**
   * "Any of these groups" (mode or, or a single group) is one filter on the union of their members.
   * "Every one of these groups" (mode and) is one filter per group, which the enclosing group can
   * only AND when its own mode is and; otherwise the filter is left on the static path.
   */
  private List<Filter> resolve(final Filter filter, final FilterMode groupMode) {
    List<String> groupIds = filter.getValues();
    if (groupIds.size() == 1 || filter.getMode() == FilterMode.or) {
      return List.of(assetsFilter(filter, groupIds));
    }
    if (groupMode != FilterMode.and) {
      return List.of(filter);
    }
    return groupIds.stream().map(groupId -> assetsFilter(filter, List.of(groupId))).toList();
  }

  private Filter assetsFilter(final Filter filter, final List<String> groupIds) {
    Set<String> members = this.assetGroupService.memberAssetIds(groupIds);
    List<String> values = members.isEmpty() ? List.of(NO_MEMBER) : new ArrayList<>(members);
    return Filter.builder()
        .id(filter.getId() != null ? filter.getId() : UUID.randomUUID().toString())
        .key(ASSETS_KEY)
        .mode(FilterMode.or)
        .operator(filter.getOperator())
        .values(values)
        .build();
  }
}
