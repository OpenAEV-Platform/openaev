package io.openaev.api.catalog;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.catalog.dto.ContentChangeOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.catalog.history.ContentHistoryService;
import io.openaev.utils.TxCtxScopeUtils;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @Operation(summary = "Installs, uninstalls and revocations of the action's payload, newest first")
  @GetMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/history")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<ContentChangeOutput> payloadHistory(TxCtx ctx, @PathVariable String actionId) {
    return contentHistoryService
        .payloadHistory(TxCtxScopeUtils.requireOneTenantOfScope(ctx), actionId)
        .stream()
        .map(ContentChangeMapper::toOutput)
        .toList();
  }

  @Operation(summary = "Installs and uninstalls of the scenario, newest first")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/history")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<ContentChangeOutput> scenarioHistory(TxCtx ctx, @PathVariable String scenarioId) {
    return contentHistoryService.scenarioHistory(ctx, scenarioId).stream()
        .map(ContentChangeMapper::toOutput)
        .toList();
  }
}
