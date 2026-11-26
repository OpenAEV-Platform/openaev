package io.openaev.api.threat_arsenal.customization;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;

import io.openaev.aop.AccessControl;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.threat_arsenal.customization.dto.PayloadCustomizationOutput;
import io.openaev.api.threat_arsenal.customization.dto.PayloadCustomizationUpdateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.payload.PayloadCustomizationService;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.mapper.ThreatArsenalMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class ThreatArsenalCustomizationApi {

  private final PayloadCustomizationService payloadCustomizationService;
  private final ThreatArsenalMapper threatArsenalMapper;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- CREATE --

  @Operation(
      summary = "Customize a Filigran payload",
      description = "A local copy that stays linked to this Filigran version and takes the newer.")
  @PostMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/customize")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalAction customize(TxCtx ctx, @PathVariable String actionId) {
    return threatArsenalMapper.toThreatArsenalAction(
        payloadCustomizationService.customize(
            writeScopeResolver.tenantForWrite(ctx, null), actionId));
  }

  // -- READ --

  @Operation(
      summary =
          "The Filigran version a customized copy is built from, and what a newer one changes")
  @GetMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/customization")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadCustomizationOutput customization(
      @RequireTenantSelector TxCtx ctx, @PathVariable String actionId) {
    return PayloadCustomizationMapper.toOutput(
        payloadCustomizationService.customization(
            TxCtxScopeUtils.requireOneTenantOfScope(ctx), actionId));
  }

  // -- UPDATE --

  @Operation(
      summary = "Bring the newer Filigran version into a customized copy",
      description =
          "Adds a version of the copy: its changes stay, and each conflicting block keeps its own"
              + " value or takes Filigran's, as chosen.")
  @PostMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/customization/update")
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalAction update(
      TxCtx ctx,
      @PathVariable String actionId,
      @Valid @RequestBody PayloadCustomizationUpdateInput input) {
    return threatArsenalMapper.toThreatArsenalAction(
        payloadCustomizationService.update(
            writeScopeResolver.tenantForWrite(ctx, null),
            actionId,
            input.payloadId(),
            input.choices()));
  }
}
