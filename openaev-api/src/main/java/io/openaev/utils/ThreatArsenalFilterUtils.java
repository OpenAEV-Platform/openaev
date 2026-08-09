package io.openaev.utils;

import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SearchPaginationInputMapper;
import jakarta.validation.constraints.NotNull;
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
      Map.ofEntries(
          Map.entry("action_labels", "injector_contract_labels"),
          Map.entry("action_platforms", "injector_contract_platforms"),
          Map.entry("action_domains", "injector_contract_domains"),
          Map.entry("action_tags", "injector_contract_tags"),
          Map.entry("action_payload_status", "injector_contract_payload_status"),
          Map.entry("action_injectors", "injector_contract_injectors"),
          Map.entry("action_updated_at", "injector_contract_updated_at"),
          Map.entry("action_author", "injector_contract_payload_author"),
          Map.entry("providing", "injector_contract_providing"));

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
    return SearchPaginationInputMapper.translateFields(input, ACTION_TO_ENTITY_FIELDS);
  }
}
