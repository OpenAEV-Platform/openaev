package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;

import io.openaev.aop.AccessControl;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.threat_arsenal.dto.PayloadUsagesOutput;
import io.openaev.api.threat_arsenal.dto.PayloadVersionChangeOutput;
import io.openaev.api.threat_arsenal.dto.PayloadVersionOutput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionIdOutput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionIdsInput;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.TxCtxScopeUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class ThreatArsenalVersionApi {

  private final PayloadVersionService payloadVersionService;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- READ --

  @Operation(summary = "Versions of the action's payload installed in the tenant, newest first")
  @GetMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/versions")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadVersionOutput> versions(
      @RequireTenantSelector TxCtx ctx, @PathVariable String actionId) {
    return payloadVersionService.versions(TxCtxScopeUtils.requireOneTenantOfScope(ctx), actionId);
  }

  @Operation(summary = "What the action's payload version changed since the version just below")
  @GetMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/changes")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadVersionChangeOutput> changes(
      @RequireTenantSelector TxCtx ctx, @PathVariable String actionId) {
    return payloadVersionService
        .changeFromPrevious(TxCtxScopeUtils.requireOneTenantOfScope(ctx), actionId)
        .map(PayloadVersionChangeMapper::toOutputs)
        .orElse(List.of());
  }

  @Operation(
      summary =
          "Scenario injects, atomic testings and chained scenario steps running the action that"
              + " the user can read, and how many others")
  @GetMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages")
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
  @GetMapping(TENANT_THREAT_ARSENAL_URL + "/payloads/{payloadId}/action")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalActionIdOutput listedAction(
      @RequireTenantSelector TxCtx ctx, @PathVariable String payloadId) {
    return new ThreatArsenalActionIdOutput(
        payloadVersionService
            .listedAction(TxCtxScopeUtils.requireOneTenantOfScope(ctx), payloadId)
            .getId());
  }

  @Operation(summary = "Listed actions with injects on an older version, with how many")
  @PostMapping(TENANT_THREAT_ARSENAL_URL + "/older-versions-in-use")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public Map<String, Long> olderVersionsInUse(
      @RequireTenantSelector TxCtx ctx, @Valid @RequestBody ThreatArsenalActionIdsInput input) {
    return payloadVersionService.olderVersionUsages(
        TxCtxScopeUtils.requireOneTenantOfScope(ctx), input.actionIds());
  }

  // -- UPDATE --

  @Operation(summary = "Keep the injects on older versions until a newer one")
  @PutMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/older-versions-kept")
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public void keepOlderVersions(TxCtx ctx, @PathVariable String actionId) {
    payloadVersionService.keepOlderVersions(
        writeScopeResolver.tenantForWrite(ctx, null), actionId, true);
  }

  // -- DELETE --

  @Operation(summary = "Show the older versions banner again")
  @DeleteMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/older-versions-kept")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public void showOlderVersions(TxCtx ctx, @PathVariable String actionId) {
    payloadVersionService.keepOlderVersions(
        writeScopeResolver.tenantForWrite(ctx, null), actionId, false);
  }
}
