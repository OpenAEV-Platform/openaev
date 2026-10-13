package io.openaev.api.payload;

import static io.openaev.api.payload.PayloadRevocationMapper.toOutput;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.payload.PayloadRevocationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PayloadRevocationApi {

  private static final String REVOCATION_PATH = "/{payloadId}/revocation";

  private final PayloadRevocationService payloadRevocationService;

  // -- READ --

  @LogExecutionTime
  @Operation(summary = "Active revocation of a payload, from its tenant or the revocation list")
  @GetMapping({PAYLOAD_URI + REVOCATION_PATH, TENANT_PAYLOAD_URI + REVOCATION_PATH})
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PAYLOAD)
  public PayloadRevocationOutput revocation(TxCtx ctx, @PathVariable String payloadId) {
    return payloadRevocationService
        .findActive(payloadId)
        .map(PayloadRevocationMapper::toOutput)
        .orElseThrow(() -> new ElementNotFoundException("Payload not revoked: " + payloadId));
  }

  // -- CREATE --

  @LogExecutionTime
  @Operation(summary = "Revoke a payload in its tenant")
  @PostMapping({PAYLOAD_URI + REVOCATION_PATH, TENANT_PAYLOAD_URI + REVOCATION_PATH})
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PAYLOAD)
  public PayloadRevocationOutput revoke(
      TxCtx ctx, @PathVariable String payloadId, @Valid @RequestBody PayloadRevocationInput input) {
    return toOutput(payloadRevocationService.revoke(payloadId, input.reason()));
  }

  // -- DELETE --

  @LogExecutionTime
  @Operation(summary = "Lift the tenant revocation of a payload")
  @DeleteMapping({PAYLOAD_URI + REVOCATION_PATH, TENANT_PAYLOAD_URI + REVOCATION_PATH})
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PAYLOAD)
  public void lift(TxCtx ctx, @PathVariable String payloadId) {
    payloadRevocationService.lift(payloadId);
  }
}
