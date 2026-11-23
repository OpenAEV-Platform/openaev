package io.openaev.service.catalog.action;

import io.openaev.service.catalog.payload.CatalogPayloadOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailableActionService {

  private final CatalogPayloadOfferService catalogPayloadOfferService;
  private final CatalogInjectorContractService catalogInjectorContractService;

  // -- LIST --

  @Transactional(readOnly = true)
  public AvailableActions available(String tenantId) {
    return new AvailableActions(
        catalogPayloadOfferService.notInstalled(tenantId),
        catalogInjectorContractService.notRegistered(tenantId));
  }
}
