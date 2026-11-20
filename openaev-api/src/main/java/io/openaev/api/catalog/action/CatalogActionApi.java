package io.openaev.api.catalog.action;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.catalog.action.dto.AvailableActionOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.catalog.action.AvailableActionService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The catalogue actions a tenant does not have yet. */
@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class CatalogActionApi {

  private final AvailableActionService availableActionService;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- READ --

  @Operation(summary = "Search catalogue actions the tenant does not have")
  @PostMapping(TENANT_CATALOG_URI + "/actions/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public Page<AvailableActionOutput> searchActions(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    return availableActionService
        .search(writeScopeResolver.tenantForWrite(ctx, null), input)
        .<AvailableActionOutput>map(
            AvailableActionMapper::toOutput, AvailableActionMapper::toOutput);
  }

  @LogExecutionTime
  @Operation(summary = "Facet counts of catalogue actions the tenant does not have")
  @PostMapping(TENANT_CATALOG_URI + "/actions/facet-counts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public Map<String, Map<String, Long>> actionFacetCounts(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    return availableActionService.facetCounts(writeScopeResolver.tenantForWrite(ctx, null), input);
  }
}
