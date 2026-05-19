package io.openaev.utils;

import io.openaev.database.model.Filters;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for handling threat-arsenal-specific search filter translations.
 *
 * <p>The threat arsenal frontend uses {@code action_*} field names, while the underlying JPA entity
 * ({@link io.openaev.database.model.InjectorContract}) exposes {@code injector_contract_*} names.
 * This class provides the canonical mapping between the two naming conventions and a utility method
 * to translate a {@link SearchPaginationInput} accordingly.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public class ThreatArsenalFilterUtils {

  private ThreatArsenalFilterUtils() {}

  /**
   * Maps {@code action_*} field names used by the frontend to the corresponding {@code
   * injector_contract_*} field names on the JPA entity.
   */
  public static final Map<String, String> ACTION_TO_ENTITY_FIELDS =
      Map.of(
          "action_labels", "injector_contract_labels",
          "action_platforms", "injector_contract_platforms",
          "action_domains", "injector_contract_domains",
          "action_tags", "injector_contract_tags",
          "action_payload_status", "injector_contract_payload_status",
          "action_injectors", "injector_contract_injectors",
          "action_updated_at", "injector_contract_updated_at");

  /**
   * Reverse mapping from {@code injector_contract_*} field names back to {@code action_*} names.
   *
   * <p>Derived automatically from {@link #ACTION_TO_ENTITY_FIELDS}.
   */
  public static final Map<String, String> ENTITY_TO_ACTION_FIELDS =
      ACTION_TO_ENTITY_FIELDS.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

  /**
   * Translates a {@link SearchPaginationInput} so that {@code action_*} filter keys and sort
   * properties are replaced by their {@code injector_contract_*} counterparts expected by the JPA
   * entity.
   *
   * <p>Keys absent from the mapping (e.g. {@code injector_contract_injector}) are kept as-is.
   *
   * @param input the original search input (potentially containing {@code action_*} keys)
   * @return a new {@link SearchPaginationInput} with translated keys
   */
  public static SearchPaginationInput translateSearchInput(
      @NotNull final SearchPaginationInput input) {
    SearchPaginationInput translated = new SearchPaginationInput();
    translated.setPage(input.getPage());
    translated.setSize(input.getSize());
    translated.setTextSearch(input.getTextSearch());

    if (input.getFilterGroup() != null) {
      Filters.FilterGroup translatedGroup = new Filters.FilterGroup();
      translatedGroup.setMode(input.getFilterGroup().getMode());
      List<Filters.Filter> translatedFilters = new ArrayList<>();
      for (Filters.Filter filter : input.getFilterGroup().getFilters()) {
        Filters.Filter copy = new Filters.Filter();
        copy.setKey(ACTION_TO_ENTITY_FIELDS.getOrDefault(filter.getKey(), filter.getKey()));
        copy.setOperator(filter.getOperator());
        copy.setValues(filter.getValues());
        copy.setMode(filter.getMode());
        translatedFilters.add(copy);
      }
      translatedGroup.setFilters(translatedFilters);
      translated.setFilterGroup(translatedGroup);
    }

    if (input.getSorts() != null) {
      List<SortField> translatedSorts = new ArrayList<>();
      for (SortField sort : input.getSorts()) {
        translatedSorts.add(
            new SortField(
                ACTION_TO_ENTITY_FIELDS.getOrDefault(sort.property(), sort.property()),
                sort.direction(),
                sort.nullHandling()));
      }
      translated.setSorts(translatedSorts);
    }

    return translated;
  }
}
