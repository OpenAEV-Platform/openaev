package io.openaev.service.payload;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Payload;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.rest.exception.AlreadyExistingException;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.UserService;
import io.openaev.service.catalog.payload.CatalogPayloadStatuses;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payload versions revoked in a tenant, by a tenant admin or by the catalogue, and lifted. */
@Service
@RequiredArgsConstructor
public class PayloadRevocationService {

  public static final String ALREADY_REVOKED = "This payload version is already revoked";
  public static final String NOT_REVOKED = "This payload version is not revoked";
  public static final String LIFT_REFUSED = "Revoked by Filigran: cannot be lifted";

  private final PayloadService payloadService;
  private final PayloadRevocationRepository payloadRevocationRepository;
  private final UserService userService;
  private final CatalogPayloadStatuses catalogPayloadStatuses;

  // -- CREATE --

  @Transactional(rollbackFor = Exception.class)
  public void revoke(TxCtx ctx, String tenantId, String payloadId, String reason) {
    Payload payload = payloadService.payload(tenantId, payloadId);
    if (isRevoked(tenantId, payloadId)) {
      throw new AlreadyExistingException(ALREADY_REVOKED);
    }
    payloadRevocationRepository.save(
        PayloadRevocation.byAdmin(payload, reason, userService.currentUserOrNull()));
  }

  // -- READ --

  public PayloadRevocation revocation(String tenantId, String payloadId) {
    return payloadRevocationRepository
        .findByTenantIdAndPayloadId(tenantId, payloadId)
        .orElseThrow(() -> new ElementNotFoundException(NOT_REVOKED));
  }

  public boolean isRevoked(String tenantId, String payloadId) {
    return payloadRevocationRepository.existsByTenantIdAndPayloadId(tenantId, payloadId);
  }

  /** The revocations of these payload versions in the tenant, by payload id. */
  public Map<String, PayloadRevocation> revocations(String tenantId, Collection<Payload> payloads) {
    return payloadRevocationRepository
        .findAllByTenantIdAndPayloadIdIn(tenantId, payloads.stream().map(Payload::getId).toList())
        .stream()
        .collect(Collectors.toMap(r -> r.getPayload().getId(), Function.identity()));
  }

  // -- EXECUTION --

  /** Why the inject must not run: the payload version it runs is revoked in its tenant. */
  public Optional<String> refusal(Inject inject) {
    return inject
        .getPayload()
        .flatMap(
            payload ->
                payloadRevocationRepository.findByTenantIdAndPayloadId(
                    payload.getTenant().getId(), payload.getId()))
        .map(revocation -> "Payload revoked: " + revocation.getReason());
  }

  /** A disabled inject never runs, so only the enabled ones count. */
  @Transactional(readOnly = true)
  public boolean anyRevoked(Collection<Inject> injects) {
    return injects.stream()
        .filter(Inject::isEnabled)
        .flatMap(inject -> inject.getPayload().stream())
        .collect(
            Collectors.groupingBy(
                payload -> payload.getTenant().getId(),
                Collectors.mapping(Payload::getId, Collectors.toSet())))
        .entrySet()
        .stream()
        .anyMatch(
            tenantPayloads ->
                payloadRevocationRepository.existsByTenantIdAndPayloadIdIn(
                    tenantPayloads.getKey(), tenantPayloads.getValue()));
  }

  // -- CATALOGUE --

  /**
   * Revokes the installed versions the catalogue revokes, and lifts its own once it clears them.
   */
  @Transactional(rollbackFor = Exception.class)
  public void applyCatalogueRevocations(TxCtx ctx, String tenantId) {
    CatalogPayloadStatuses.Snapshot catalogue = catalogPayloadStatuses.current();
    Map<String, PayloadRevocation> revocations =
        payloadRevocationRepository.findAllByTenantId(tenantId).stream()
            .collect(Collectors.toMap(r -> r.getPayload().getId(), Function.identity()));
    for (String externalId : catalogue.revokedExternalIds()) {
      payloadService.versions(tenantId, externalId).stream()
          .filter(catalogue::revokes)
          .filter(payload -> !revocations.containsKey(payload.getId()))
          .forEach(
              payload -> payloadRevocationRepository.save(PayloadRevocation.byCatalogue(payload)));
    }
    revocations.values().stream()
        .filter(PayloadRevocation::isByCatalogue)
        .filter(revocation -> catalogue.clears(revocation.getPayload()))
        .forEach(payloadRevocationRepository::delete);
  }

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  public void lift(TxCtx ctx, String tenantId, String payloadId) {
    if (catalogPayloadStatuses.current().revokes(payloadService.payload(tenantId, payloadId))) {
      throw new BadRequestException(LIFT_REFUSED);
    }
    payloadRevocationRepository.delete(revocation(tenantId, payloadId));
  }
}
