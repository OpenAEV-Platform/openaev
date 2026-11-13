package io.openaev.api.payload;

import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;

import io.openaev.aop.AccessControl;
import io.openaev.api.catalog.CatalogFeature;
import io.openaev.api.payload.dto.PayloadRevocationInput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.payload.PayloadRevocationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PayloadRevocationApi {

  private static final String REVOCATION_URI = TENANT_PAYLOAD_URI + "/{payloadId}/revocation";

  private final PayloadRevocationService payloadRevocationService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final CatalogFeature catalogFeature;

  // -- CREATE --

  @Operation(summary = "Revoke a payload version: it no longer runs in the tenant")
  @PostMapping(REVOCATION_URI)
  @ResponseStatus(HttpStatus.CREATED)
  @AccessControl(actionPerformed = Action.REVOKE, resourceType = ResourceType.THREAT_ARSENAL)
  @Transactional(propagation = Propagation.SUPPORTS)
  public void revoke(
      TxCtx ctx, @PathVariable String payloadId, @Valid @RequestBody PayloadRevocationInput input) {
    catalogFeature.require();
    payloadRevocationService.revoke(
        ctx, writeScopeResolver.tenantForWrite(ctx, null), payloadId, input.reason());
  }

  // -- DELETE --

  @Operation(summary = "Lift the revocation of a payload version")
  @DeleteMapping(REVOCATION_URI)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AccessControl(actionPerformed = Action.REVOKE, resourceType = ResourceType.THREAT_ARSENAL)
  @Transactional(propagation = Propagation.SUPPORTS)
  public void lift(TxCtx ctx, @PathVariable String payloadId) {
    catalogFeature.require();
    payloadRevocationService.lift(ctx, writeScopeResolver.tenantForWrite(ctx, null), payloadId);
  }
}
