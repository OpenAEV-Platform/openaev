package io.openaev.api.threat_arsenal.move;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;

import io.openaev.aop.AccessControl;
import io.openaev.api.catalog.CatalogFeature;
import io.openaev.api.threat_arsenal.move.dto.PayloadUsageMoveInput;
import io.openaev.api.threat_arsenal.move.dto.PayloadUsageMoveOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.payload.PayloadUsageMoveService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PayloadUsageMoveApi {

  private final PayloadUsageMoveService payloadUsageMoveService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final CatalogFeature catalogFeature;

  // -- UPDATE --

  @Operation(
      summary = "Move injects and chained steps to another version of the action's payload",
      description =
          "Each one needs the right to update its scenario or atomic testing, and arguments the"
              + " target version accepts; the others stay and are returned with the reason.")
  @PostMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages/move")
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadUsageMoveOutput moveUsages(
      TxCtx ctx, @PathVariable String actionId, @Valid @RequestBody PayloadUsageMoveInput input) {
    catalogFeature.require();
    return PayloadUsageMoveMapper.toOutput(
        payloadUsageMoveService.move(
            ctx,
            writeScopeResolver.tenantForWrite(ctx, null),
            actionId,
            input.targetPayloadId(),
            input.injectIds(),
            input.stepIds()));
  }
}
