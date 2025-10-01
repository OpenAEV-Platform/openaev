package io.openaev.utils;

import static java.util.Optional.ofNullable;

import io.openaev.database.model.Filters;
import io.openaev.database.model.Payload;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class ArchitectureFilterUtils {

  private static final String PAYLOAD_EXECUTION_ARCH = "payload_execution_arch";
  private static final String INJECTOR_CONTRACT_ARCH = "injector_contract_arch";
  private static final String ENDPOINT_ARCH = "endpoint_arch";
  private static final String ALL_ARCHITECTURES = "ALL_ARCHITECTURES";

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
