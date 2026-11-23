package io.openaev.api.catalog.action;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.catalog.action.dto.AvailableActionOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.catalog.action.AvailableActionService;
import io.openaev.service.catalog.action.AvailableActions;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The catalogue actions a tenant does not have yet. */
@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class CatalogActionApi {

  private final AvailableActionService availableActionService;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- READ --

  @Operation(summary = "Catalogue actions the tenant does not have yet")
  @GetMapping(TENANT_CATALOG_URI + "/actions")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<AvailableActionOutput> actions(TxCtx ctx) {
    AvailableActions available =
        availableActionService.available(writeScopeResolver.tenantForWrite(ctx, null));
    return Stream.concat(
            available.payloads().stream().map(AvailableActionMapper::toOutput),
            available.contracts().stream().map(AvailableActionMapper::toOutput))
        .toList();
  }
}
