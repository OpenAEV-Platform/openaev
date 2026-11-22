package io.openaev.service.catalog.action;

import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.CatalogConnectorInjectorContractRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogInjectorContractService {

  private final CatalogConnectorInjectorContractRepository
      catalogConnectorInjectorContractRepository;

  // -- LIST --

  @Transactional(readOnly = true)
  public List<CatalogConnectorInjectorContract> notRegistered(String tenantId) {
    return catalogConnectorInjectorContractRepository.findAllOfInjectorsNotRegisteredInTenant(
        tenantId, ConnectorType.INJECTOR);
  }
}
