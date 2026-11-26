package io.openaev.api.catalog;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.catalog.dto.ContentChangeOutput;
import io.openaev.api.catalog.dto.PayloadContentChangeOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.catalog.history.ContentHistoryService;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who installed, uninstalled, revoked or lifted each version of a payload or a scenario, and when.
 */
@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class ContentHistoryApi {

  private final ContentHistoryService contentHistoryService;

  // -- READ --

  @Operation(summary = "Payload history, newest first, with each version's changes")
  @PostMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/history/search")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public Page<PayloadContentChangeOutput> payloadHistory(
      TxCtx ctx, @PathVariable String actionId, @RequestBody @Valid SearchPaginationInput input) {
    return contentHistoryService
        .payloadHistory(TxCtxScopeUtils.requireOneTenantOfScope(ctx), actionId, input)
        .map(ContentChangeMapper::toPayloadOutput);
  }

  @Operation(summary = "Scenario history, newest first")
  @PostMapping(TENANT_SCENARIO_URI + "/{scenarioId}/history/search")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Page<ContentChangeOutput> scenarioHistory(
      TxCtx ctx, @PathVariable String scenarioId, @RequestBody @Valid SearchPaginationInput input) {
    return contentHistoryService
        .scenarioHistory(ctx, scenarioId, input)
        .map(ContentChangeMapper::toOutput);
  }
}
