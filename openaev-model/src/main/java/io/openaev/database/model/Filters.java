package io.openaev.database.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class Filters {

  public enum FilterMode {
    and,
    or
  }

  public enum FilterOperator {
    eq,
    not_eq,
    contains,
    not_contains,
    starts_with,
    not_starts_with,
    gt,
    gte,
    lt,
    lte,
    empty,
    not_empty,
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FilterGroup {

    @NotNull @Builder.Default private FilterMode mode = FilterMode.and;
    @Builder.Default private List<Filter> filters = new ArrayList<>();

    /**
     * asset_group_dynamic_filter in AssetGroup is now not null so we added a default not null value
     * for the java object AssetGroup and the SQL table asset_groups Tests to protect this are
     * implemented in AssetGroupApiTest Don't forget to update the default value for each case
     * described above if needed !
     */
    public static FilterGroup defaultFilterGroup() {
      FilterGroup filterGroup = new FilterGroup();
      filterGroup.setMode(FilterMode.and);
      filterGroup.setFilters(new ArrayList<>());
      return filterGroup;
    }

    public static FilterGroup filterGroupWithFilters(List<Filter> filters) {
      FilterGroup filterGroup = new FilterGroup();
      filterGroup.setMode(FilterMode.and);
      filterGroup.setFilters(filters);
      return filterGroup;
    }

    // -- UTILS --
    public Optional<Filter> findById(@NotBlank final String filterId) {
      if (this.getFilters() == null) {
        return Optional.empty();
      }
      return this.getFilters().stream().filter(filter -> matchesId(filter, filterId)).findFirst();
    }

    public void removeById(@NotBlank final String filterId) {
      if (this.getFilters() == null) {
        return;
      }
      List<Filter> newFilters =
          this.getFilters().stream().filter(filter -> !matchesId(filter, filterId)).toList();
      this.setFilters(newFilters);
    }

    public Optional<Filter> findByKey(@NotBlank final String filterKey) {
      if (this.getFilters() == null) {
        return Optional.empty();
      }

      return this.getFilters().stream().filter(filter -> matchesKey(filter, filterKey)).findFirst();
    }

    public void removeByKey(@NotBlank final String filterKey) {
      if (this.getFilters() == null) {
        return;
      }

      List<Filter> newFilters =
          this.getFilters().stream().filter(filter -> !matchesKey(filter, filterKey)).toList();
      this.setFilters(newFilters);
    }

    private static boolean matchesId(
        @Nullable final Filter filter, @NotBlank final String filterId) {
      return filter != null && Objects.equals(filter.getId(), filterId);
    }

    private static boolean matchesKey(
        @Nullable final Filter filter, @NotBlank final String filterKey) {
      return filter != null && Objects.equals(filter.getKey(), filterKey);
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Filter {
    @NotNull private String id;
    @NotNull private String key;
    @Builder.Default private FilterMode mode = FilterMode.and;
    private List<String> values;
    @Builder.Default private FilterOperator operator = FilterOperator.eq;

    public static Filter getNewDefaultEqualFilter(String key, List<String> values) {
      Filter filter = new Filter();
      filter.setId(UUID.randomUUID().toString());
      filter.setKey(key);
      filter.setMode(Filters.FilterMode.or);
      filter.setOperator(Filters.FilterOperator.eq);
      filter.setValues(values);
      return filter;
    }
  }

  public static boolean isEmptyFilterGroup(@Nullable final FilterGroup filterGroup) {
    return filterGroup == null
        || filterGroup.getFilters() == null
        || filterGroup.getFilters().isEmpty();
  }
}
