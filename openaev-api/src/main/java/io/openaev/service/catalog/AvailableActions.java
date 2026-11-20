package io.openaev.service.catalog;

import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.catalog.CatalogPayload;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * A page of what the catalogue offers a tenant: payloads to install, then the actions of
 * integrations to deploy once the payloads run out.
 */
public record AvailableActions(
    List<CatalogPayload> payloads,
    List<CatalogConnectorInjectorContract> contracts,
    Pageable pageable,
    long total) {

  static AvailableActions of(
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

  public <R> Page<R> map(
      Function<CatalogPayload, R> payload, Function<CatalogConnectorInjectorContract, R> contract) {
    return new PageImpl<>(
        Stream.concat(payloads.stream().map(payload), contracts.stream().map(contract)).toList(),
        pageable,
        total);
  }
}
