package io.openaev.utils;

import static java.util.Optional.ofNullable;

import io.openaev.database.model.Filters;
import io.openaev.database.model.Payload;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for handling architecture-related search filters.
 *
 * <p>Provides methods for processing architecture filters in search queries, ensuring that
 * architecture-agnostic payloads (ALL_ARCHITECTURES) are properly included in filtered results.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.Payload.PAYLOAD_EXECUTION_ARCH
 */
public class ArchitectureFilterUtils {

  private ArchitectureFilterUtils() {}

  /** Filter key for payload execution architecture. */
  private static final String PAYLOAD_EXECUTION_ARCH = "payload_execution_arch";

  /** Filter key for injector contract architecture. */
  private static final String INJECTOR_CONTRACT_ARCH = "injector_contract_arch";

  /** Filter key for endpoint architecture. */
  private static final String ENDPOINT_ARCH = "endpoint_arch";

  /** Special value representing payloads compatible with all architectures. */
  private static final String ALL_ARCHITECTURES = "ALL_ARCHITECTURES";

  /**
   * Handles architecture filter by adding ALL_ARCHITECTURES to specific architecture queries.
   *
   * <p>When filtering for a specific architecture (x86_64 or arm64), this method adds
   * ALL_ARCHITECTURES to the filter values to ensure architecture-agnostic payloads are included in
   * results.
   *
   * @param searchPaginationInput the search input containing filters to modify
   * @return the modified search input with updated architecture filter values
   */
  public static SearchPaginationInput handleArchitectureFilter(
      @NotNull final SearchPaginationInput searchPaginationInput) {

    Optional<Filters.Filter> filterOpt =
        ofNullable(searchPaginationInput.getFilterGroup())
            .flatMap(
                f -> {
                  Optional<Filters.Filter> filter = f.findByKey(PAYLOAD_EXECUTION_ARCH);
                  if (filter.isPresent()) {
                    return filter;
                  } else {
                    return f.findByKey(INJECTOR_CONTRACT_ARCH);
                  }
                });

    filterOpt.ifPresent(
        payloadFilter -> {
          if (payloadFilter.getValues().contains(Payload.PAYLOAD_EXECUTION_ARCH.x86_64.name())
              || payloadFilter.getValues().contains(Payload.PAYLOAD_EXECUTION_ARCH.arm64.name())) {
            payloadFilter.getValues().add(ALL_ARCHITECTURES);
          }
        });

    return searchPaginationInput;
  }

  /**
   * Handles endpoint architecture filter by removing ALL_ARCHITECTURES placeholder.
   *
   * <p>Removes the ALL_ARCHITECTURES value from endpoint filters since endpoints always have a
   * specific architecture. If this results in an empty filter, the filter is removed entirely.
   *
   * @param searchPaginationInput the search input containing filters to modify
   * @return the modified search input with cleaned endpoint architecture filter
   */
  public static SearchPaginationInput handleEndpointFilter(
      @NotNull final SearchPaginationInput searchPaginationInput) {

    Optional<Filters.Filter> endpointFilterOpt =
        ofNullable(searchPaginationInput.getFilterGroup()).flatMap(f -> f.findByKey(ENDPOINT_ARCH));

    endpointFilterOpt.ifPresent(
        endpointFilter -> {
          if (endpointFilter.getValues().contains(ALL_ARCHITECTURES)) {
            endpointFilter.getValues().remove(ALL_ARCHITECTURES);
          }
          if (endpointFilter.getValues().isEmpty()) {
            searchPaginationInput.getFilterGroup().removeByKey(ENDPOINT_ARCH);
          }
        });

    return searchPaginationInput;
  }
}
