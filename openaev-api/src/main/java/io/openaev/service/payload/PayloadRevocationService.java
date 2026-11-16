package io.openaev.service.payload;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.rest.exception.AlreadyExistingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payload versions a tenant admin revokes in the tenant, and lifts. */
@Service
@RequiredArgsConstructor
public class PayloadRevocationService {

  public static final String ALREADY_REVOKED = "This payload version is already revoked";
  public static final String NOT_REVOKED = "This payload version is not revoked";

  private final PayloadService payloadService;
  private final PayloadRevocationRepository payloadRevocationRepository;
  private final UserService userService;

  // -- CREATE --

  @Transactional(rollbackFor = Exception.class)
  public void revoke(TxCtx ctx, String tenantId, String payloadId, String reason) {
    Payload payload = payloadService.payload(tenantId, payloadId);
    if (payloadRevocationRepository.existsByTenantIdAndPayloadId(tenantId, payloadId)) {
      throw new AlreadyExistingException(ALREADY_REVOKED);
    }
    PayloadRevocation revocation = new PayloadRevocation();
    revocation.setTenant(payload.getTenant());
    revocation.setPayload(payload);
    revocation.setReason(reason);
    revocation.setUser(userService.currentUserOrNull());
    payloadRevocationRepository.save(revocation);
  }

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  public void lift(TxCtx ctx, String tenantId, String payloadId) {
    payloadService.payload(tenantId, payloadId);
    payloadRevocationRepository.delete(
        payloadRevocationRepository
            .findByTenantIdAndPayloadId(tenantId, payloadId)
            .orElseThrow(() -> new ElementNotFoundException(NOT_REVOKED)));
  }
}
