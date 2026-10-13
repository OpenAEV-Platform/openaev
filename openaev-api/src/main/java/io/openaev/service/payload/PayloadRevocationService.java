package io.openaev.service.payload;

import static java.time.Instant.now;

import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.ContentRevocationRepository;
import io.openaev.rest.exception.AlreadyExistingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.service.content.ContentHistoryService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payload revocations: the revocation list for every tenant, and tenant revocations (F34). */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadRevocationService {

  private final ContentRevocationRepository contentRevocationRepository;
  private final PayloadVersionService payloadVersionService;
  private final ContentHistoryService contentHistoryService;
  private final UserService userService;

  // -- LIST --

  /** The only revocation check: execution, launch and pickers go through it. */
  @Transactional(readOnly = true)
  public boolean isRevoked(Payload payload) {
    return !revokedIds(List.of(payload)).isEmpty();
  }

  @Transactional(readOnly = true)
  public Set<String> revokedIds(Collection<Payload> payloads) {
    return payloadVersionService.revokedIds(payloads);
  }

  @Transactional(readOnly = true)
  public Set<String> revokedIdsAmong(Collection<String> payloadIds) {
    if (payloadIds.isEmpty()) {
      return Set.of();
    }
    return revokedIds(payloadVersionService.findAllById(payloadIds));
  }

  /** The active revocation of a payload: its tenant's first, else the revocation list's. */
  @Transactional(readOnly = true)
  public Optional<ContentRevocation> findActive(String payloadId) {
    Payload payload = payload(payloadId);
    Optional<ContentRevocation> tenantRevocation =
        contentRevocationRepository.findActiveByPayload(
            payload.getId(), payload.getTenant().getId());
    if (tenantRevocation.isPresent() || !PayloadVersionService.isManaged(payload)) {
      return tenantRevocation;
    }
    return contentRevocationRepository
        .findAllByTypeAndExternalIdAndTenantIsNullAndLiftedAtIsNull(
            ContentType.PAYLOAD, payload.getExternalId())
        .stream()
        .filter(r -> payload.getVersion().equals(r.getVersion()))
        .findFirst();
  }

  // -- CREATE --

  /** Revokes a payload in its tenant; any payload, managed version or local one. */
  public ContentRevocation revoke(String payloadId, String reason) {
    Payload payload = payload(payloadId);
    if (isRevoked(payload)) {
      throw new AlreadyExistingException("Payload already revoked: " + payloadId);
    }
    ContentRevocation revocation = new ContentRevocation();
    revocation.setType(ContentType.PAYLOAD);
    revocation.setPayload(payload);
    revocation.setReason(reason);
    revocation.setRevokedBy(userService.currentUser());
    revocation.setTenant(payload.getTenant());
    revocation.setPayloadPreviousStatus(payload.getStatus());
    revocation = contentRevocationRepository.save(revocation);
    payloadVersionService.setStatus(payload, Payload.PAYLOAD_STATUS.REVOKED);
    payloadVersionService.refreshLatest(payload);
    contentHistoryService.payloadRevoked(payload);
    return revocation;
  }

  // -- DELETE --

  /** Lifts the tenant's revocation; a revocation from the list is lifted by a newer list only. */
  public void lift(String payloadId) {
    Payload payload = payload(payloadId);
    ContentRevocation revocation =
        contentRevocationRepository
            .findActiveByPayload(payload.getId(), payload.getTenant().getId())
            .orElseThrow(() -> new ElementNotFoundException("No tenant revocation: " + payloadId));
    revocation.setLiftedAt(now());
    contentRevocationRepository.save(revocation);
    payloadVersionService.setStatus(
        payload,
        Optional.ofNullable(revocation.getPayloadPreviousStatus())
            .orElse(Payload.PAYLOAD_STATUS.UNVERIFIED));
    payloadVersionService.refreshLatest(payload);
    contentHistoryService.payloadRevocationLifted(payload);
  }

  private Payload payload(String payloadId) {
    return payloadVersionService.findById(payloadId);
  }
}
