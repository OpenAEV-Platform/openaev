package io.openaev.utils.helpers;

import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadIdentity;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.service.payload.PayloadVersionService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Turns persisted payloads into versions of a catalogue payload, the way an install does. */
@Component
public class PayloadVersionTestHelper {

  public static final String REPOSITORY = "filigran/openaev-payloads";

  @Autowired private PayloadVersionService payloadVersionService;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private EntityManager entityManager;

  /** Moves the payload into the managed identity of this external ID, as this version. */
  public Payload asManagedVersion(Payload payload, String externalId, String version) {
    PayloadIdentity identity =
        payloadVersionService.findOrCreateManagedIdentity(
            payload.getTenant().getId(), REPOSITORY, externalId);
    return payloadVersionService.moveToIdentity(payload, identity, version);
  }

  /**
   * Deletes a payload (if a contract cascade has not already) and the identities left empty, then
   * clears the session: a cleanup that deletes the collector type next would otherwise flush a
   * dangling reference.
   */
  public void delete(String payloadId) {
    payloadRepository.findById(payloadId).ifPresent(payloadRepository::delete);
    entityManager.flush();
    entityManager
        .createNativeQuery(
            "DELETE FROM payload_identities pi WHERE NOT EXISTS"
                + " (SELECT 1 FROM payloads p WHERE p.identity_id = pi.identity_id)")
        .executeUpdate();
    entityManager.clear();
  }
}
