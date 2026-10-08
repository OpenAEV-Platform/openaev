package io.openaev.service.payload;

import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.ContentRevocationRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.SemanticVersion;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payload versions (ADR-011). Sole writer of {@code payload_is_latest}, so readers filter on it
 * instead of resolving versions themselves.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadVersionService {

  private final PayloadRepository payloadRepository;
  private final ContentRevocationRepository contentRevocationRepository;
  private final EntityManager entityManager;

  public static boolean isManaged(Payload payload) {
    return payload.getVersion() != null;
  }

  // -- LIST --

  @Transactional(readOnly = true)
  public List<Payload> findAllByExternalId(String externalId) {
    return payloadRepository.findAllByExternalId(externalId);
  }

  @Transactional(readOnly = true)
  public Optional<Payload> findLatest(String externalId) {
    return payloadRepository.findByExternalIdAndLatestTrue(externalId);
  }

  // -- UPDATE --

  /**
   * Recomputes the latest flag of an external ID: the highest non-revoked managed version, else the
   * unversioned row. To call after every install, revocation, lift or migration of that ID.
   */
  public Optional<Payload> refreshLatest(String externalId) {
    List<Payload> payloads = payloadRepository.findAllByExternalId(externalId);
    Optional<Payload> latest = latestNonRevoked(payloads, revokedVersions(externalId));
    // Clear before set: the partial unique index allows one latest row per external ID.
    List<Payload> cleared = new ArrayList<>();
    for (Payload payload : payloads) {
      boolean isNewLatest = latest.map(l -> l.getId().equals(payload.getId())).orElse(false);
      if (payload.isLatest() && !isNewLatest) {
        payload.setLatest(false);
        cleared.add(payload);
      }
    }
    payloadRepository.saveAll(cleared);
    entityManager.flush();
    latest
        .filter(l -> !l.isLatest())
        .ifPresent(
            l -> {
              l.setLatest(true);
              payloadRepository.save(l);
            });
    return latest;
  }

  private Set<String> revokedVersions(String externalId) {
    return contentRevocationRepository
        .findAllByTypeAndExternalIdAndLiftedAtIsNull(
            ContentRevocation.CONTENT_TYPE.PAYLOAD, externalId)
        .stream()
        .map(ContentRevocation::getVersion)
        .collect(Collectors.toSet());
  }

  private static Optional<Payload> latestNonRevoked(
      Collection<Payload> payloads, Set<String> revokedVersions) {
    List<Payload> candidates =
        payloads.stream()
            .filter(p -> p.getStatus() != Payload.PAYLOAD_STATUS.REVOKED)
            .filter(p -> !isManaged(p) || !revokedVersions.contains(p.getVersion()))
            .toList();
    Optional<Payload> latestManaged =
        candidates.stream()
            .filter(PayloadVersionService::isManaged)
            .max(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER));
    return latestManaged.isPresent()
        ? latestManaged
        : candidates.stream().filter(p -> !isManaged(p)).findFirst();
  }
}
