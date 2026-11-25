package io.openaev.database.model.payload;

import io.openaev.database.model.Payload;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.catalog.SemanticVersion;
import jakarta.persistence.PreRemove;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Deleting the latest version of a payload shows the highest version left, whatever deletes it. */
@Component
@RequiredArgsConstructor
public class PayloadLatestVersionListener {

  // Provider: the repository needs the entity manager factory, which builds this listener.
  private final ObjectProvider<PayloadRepository> payloadRepository;

  @PreRemove
  void showHighestLeft(Payload payload) {
    PayloadIdentity identity = payload.getIdentity();
    Payload latest = identity.getLatestVersion();
    if (latest == null || !latest.getId().equals(payload.getId())) {
      return;
    }
    identity.setLatestVersion(
        SemanticVersion.highest(
                payloadRepository.getObject().findAllByIdentityId(identity.getId()).stream()
                    .filter(version -> !version.getId().equals(payload.getId()))
                    .toList(),
                Payload::getVersion)
            .orElse(null));
  }
}
