package io.openaev.service.targets.search;

import io.openaev.database.model.Filters;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTarget;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public abstract class SearchAdaptorBase {
  protected final Map<String, String> fieldTranslations = new HashMap<>();

  public abstract Page<InjectTarget> search(SearchPaginationInput input, Inject scopedInject);

  public abstract List<FilterUtilsJpa.Option> getOptionsForInject(
      Inject scopedInject, String textSearch);

  public abstract List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids);

  protected SearchPaginationInput translate(SearchPaginationInput input, Inject scopedInject) {
    SearchPaginationInput newInput = new SearchPaginationInput();

    // swap the filters
    List<Filters.Filter> newFilters = new ArrayList<>();
    for (Filters.Filter filter : input.getFilterGroup().getFilters()) {
      if (fieldTranslations.containsKey(filter.getKey())) {
        Filters.Filter newFilter = new Filters.Filter();
        newFilter.setKey(fieldTranslations.get(filter.getKey()));
        newFilter.setOperator(filter.getOperator());
        newFilter.setValues(filter.getValues());
        newFilter.setMode(filter.getMode());
        newFilters.add(newFilter);
      }
    }

    // the POST payload might or might not have a caller-defined "target_injects" filter
    // this filter is useful for some (not all) target entity types and is added
    // dynamically here when missing to enable scoping the search on a specific inject.
    // Also avoid double adding this filter if it's already in the collection
    if (fieldTranslations.containsKey("target_injects")
        && newFilters.stream()
            .noneMatch(filter -> filter.getKey().equals(fieldTranslations.get("target_injects")))) {
      // add search term on inject scope
      Filters.Filter injectScopeFilter = new Filters.Filter();
      injectScopeFilter.setMode(Filters.FilterMode.and);
      injectScopeFilter.setOperator(Filters.FilterOperator.eq);
      injectScopeFilter.setValues(List.of(scopedInject.getId()));
      injectScopeFilter.setKey(fieldTranslations.get("target_injects"));
      newFilters.add(injectScopeFilter);
    }

    Filters.FilterGroup newFilterGroup = new Filters.FilterGroup();
    newFilterGroup.setFilters(newFilters);
    newFilterGroup.setMode(input.getFilterGroup().getMode());
    newInput.setFilterGroup(newFilterGroup);

    // mind the sorts
    List<SortField> newSorts = new ArrayList<>();
    SortField defaultSort = new SortField(fieldTranslations.get("target_name"), "ASC");
    List<SortField> currentSorts =
        input.getSorts() == null ? List.of(defaultSort) : input.getSorts();
    for (SortField sortField : currentSorts) {
      if (fieldTranslations.containsKey(sortField.property())) {
        newSorts.add(
            new SortField(fieldTranslations.get(sortField.property()), sortField.direction()));
      }
    }

    if (newSorts.isEmpty()) {
      newSorts.add(defaultSort);
    }
    newInput.setSorts(newSorts);

    // copy the rest of the attributes
    newInput.setPage(input.getPage());
    newInput.setSize(input.getSize());
    newInput.setTextSearch(input.getTextSearch());

    return newInput;
  }
}
