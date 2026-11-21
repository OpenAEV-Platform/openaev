package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;

import io.openaev.aop.AccessControl;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.threat_arsenal.dto.PayloadUsagesOutput;
import io.openaev.api.threat_arsenal.dto.PayloadVersionOutput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionIdOutput;
import io.openaev.config.RequireTenantSelector;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.TxCtxScopeUtils;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class ThreatArsenalVersionApi {

  private final PayloadVersionService payloadVersionService;

  // -- READ --

  @Operation(summary = "Versions of the action's payload installed in the tenant, newest first")
  @GetMapping({
    THREAT_ARSENAL_URL + "/{actionId}/versions",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/versions"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadVersionOutput> versions(
      @RequireTenantSelector TxCtx ctx, @PathVariable String actionId) {
    return payloadVersionService.versions(TxCtxScopeUtils.requireOneTenantOfScope(ctx), actionId);
  }

  @Operation(
      summary =
          "Scenario injects, atomic testings and chained scenario steps running the action that"
              + " the user can read, and how many others")
  @GetMapping({
    THREAT_ARSENAL_URL + "/{actionId}/usages",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadUsagesOutput usages(
      @RequireTenantSelector TxCtx ctx, @PathVariable String actionId) {
    return payloadVersionService.usages(TxCtxScopeUtils.requireOneTenantOfScope(ctx), actionId);
  }

  @Operation(summary = "The action listed for a payload version, for links that only know it")
  @GetMapping({
    THREAT_ARSENAL_URL + "/payloads/{payloadId}/action",
    TENANT_THREAT_ARSENAL_URL + "/payloads/{payloadId}/action"
  })
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalActionIdOutput listedAction(
      @RequireTenantSelector TxCtx ctx, @PathVariable String payloadId) {
    catalogFeature.require();
    return new ThreatArsenalActionIdOutput(
        payloadVersionService
            .listedAction(TxCtxScopeUtils.requireOneTenantOfScope(ctx), payloadId)
            .getId());
  }
}
