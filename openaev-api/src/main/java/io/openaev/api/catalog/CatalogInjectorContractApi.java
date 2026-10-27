package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.CATALOG_URI;
import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.catalog.CatalogInjectorContractService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CatalogInjectorContractApi {

  private final CatalogInjectorContractService catalogInjectorContractService;

  @LogExecutionTime
  @Operation(summary = "Contracts of the catalogue injectors not running on the platform yet")
  @GetMapping({CATALOG_URI + "/injector-contracts", TENANT_CATALOG_URI + "/injector-contracts"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<CatalogInjectorContractOutput> injectorContracts(TxCtx ctx) {
    return catalogInjectorContractService.listAvailable();
  }
}
