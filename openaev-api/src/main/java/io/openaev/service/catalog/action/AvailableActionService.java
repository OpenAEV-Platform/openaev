package io.openaev.service.catalog.action;

import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.service.catalog.CatalogFacet;
import io.openaev.service.catalog.payload.CatalogPayloadOfferService;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Available tab of the Threat Arsenal: catalogue payloads, then integration actions. */
@Service
@RequiredArgsConstructor
public class AvailableActionService {

  private static final List<CatalogFacet> FACETS =
      List.of(
          new CatalogFacet.Grouped(CatalogFacet.DOMAIN_KEY),
          CatalogFacet.PLATFORMS,
          new CatalogFacet.Grouped(CatalogFacet.AUTHOR_KEY));

  private final CatalogPayloadOfferService catalogPayloadOfferService;
  private final CatalogInjectorContractService catalogInjectorContractService;

  // -- SEARCH --

  @Transactional(readOnly = true)
  public AvailableActions search(String tenantId, SearchPaginationInput input) {
    return afterPayloads(
        catalogPayloadOfferService.searchNotInstalled(tenantId, input, FACETS),
        catalogInjectorContractService.notRegistered(tenantId, input));
  }

  /** The payload and integration counts of each facet, added up. */
  @Transactional(readOnly = true)
  public Map<String, Map<String, Long>> facetCounts(String tenantId, SearchPaginationInput input) {
    Map<String, Map<String, Long>> counts =
        catalogPayloadOfferService.notInstalledFacetCounts(tenantId, input, FACETS);
    catalogInjectorContractService
        .notRegisteredFacetCounts(tenantId, input, FACETS)
        .forEach(
            (key, values) ->
                values.forEach(
                    (value, count) ->
                        counts
                            .computeIfAbsent(key, k -> new LinkedHashMap<>())
                            .merge(value, count, Long::sum)));
    return counts;
  }

  // -- STEPS --

  // The contracts follow the payloads: a page past the payloads shows the next contracts.
  private static AvailableActions afterPayloads(
      Page<CatalogPayload> payloads, List<CatalogConnectorInjectorContract> contracts) {
    long offset = payloads.getPageable().getOffset() - payloads.getTotalElements();
    int from = Math.clamp(offset, 0, contracts.size());
    int to = Math.clamp(offset + payloads.getSize(), 0, contracts.size());
    return new AvailableActions(
        payloads.getContent(),
        contracts.subList(from, to),
        payloads.getPageable(),
        payloads.getTotalElements() + contracts.size());
  }
}
