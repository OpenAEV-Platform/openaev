package io.openaev.database.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Data;

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
  public static class FilterGroup {

    @NotNull private FilterMode mode; // Between filters
    private List<Filter> filters = new ArrayList<>();

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

    // -- UTILS --

    public Optional<Filter> findByKey(@NotBlank final String filterKey) {
      if (this.getFilters() == null) {
        return Optional.empty();
      }

      return this.getFilters().stream()
          .filter(filter -> filter.getKey().equals(filterKey))
          .findFirst();
    }

    public void removeByKey(@NotBlank final String filterKey) {
      if (this.getFilters() == null) {
        return;
      }

      List<Filter> newFilters =
          this.getFilters().stream().filter(filter -> !filter.getKey().equals(filterKey)).toList();
      this.setFilters(newFilters);
    }
  }

  @Data
  public static class Filter {

    @NotNull private String key;
    private FilterMode mode; // Between values: name = name1 OR name = name2
    private List<String> values;
    private FilterOperator operator;

    public static Filter getNewDefaultEqualFilter(String key, List<String> values) {
      Filter filter = new Filter();
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
