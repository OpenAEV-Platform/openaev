package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.CATALOG_URI;
import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.catalog.RevocationListService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RevocationListApi {

  private static final String PATH = "/revocation-lists";

  private final RevocationListService revocationListService;

  @LogExecutionTime
  @Operation(summary = "The last revocation list received from each content repository")
  @GetMapping({CATALOG_URI + PATH, TENANT_CATALOG_URI + PATH})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<RevocationListOutput> revocationLists(TxCtx ctx) {
    return revocationListService.findAll();
  }

  @LogExecutionTime
  @Operation(summary = "Apply the revocation list of a content repository")
  @PostMapping(CATALOG_URI + PATH)
  @Transactional(rollbackFor = Exception.class)
  // A list applies to every tenant: platform administrators only.
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public RevocationListOutput apply(TxCtx ctx, @RequestBody RevocationList list) {
    return revocationListService.apply(list);
  }
}
