package io.openaev.service.catalog.action;

import io.openaev.service.catalog.payload.CatalogPayloadOfferService;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailableActionService {

  private final CatalogPayloadOfferService catalogPayloadOfferService;
  private final CatalogInjectorContractService catalogInjectorContractService;

  // -- SEARCH --

  @Transactional(readOnly = true)
  public AvailableActions search(String tenantId, SearchPaginationInput input) {
    return AvailableActions.of(
        catalogPayloadOfferService.searchNotInstalled(tenantId, input),
        catalogInjectorContractService.notRegistered(tenantId, input));
  }

  /** The payload and integration counts of each facet, added up. */
  @Transactional(readOnly = true)
  public Map<String, Map<String, Long>> facetCounts(String tenantId, SearchPaginationInput input) {
    Map<String, Map<String, Long>> counts =
        catalogPayloadOfferService.notInstalledFacetCounts(tenantId, input);
    catalogInjectorContractService
        .notRegisteredFacetCounts(tenantId, input)
        .forEach(
            (key, values) ->
                values.forEach(
                    (value, count) ->
                        counts
                            .computeIfAbsent(key, k -> new LinkedHashMap<>())
                            .merge(value, count, Long::sum)));
    return counts;
  }
}
