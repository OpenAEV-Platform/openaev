package io.openaev.database.model.payload;

import io.openaev.database.model.Payload;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.exception.CatalogPayloadReadOnlyException;
import io.openaev.exception.PayloadInUseException;
import jakarta.persistence.PreRemove;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Refuses deleting a catalogue version, or a payload that injects run, whatever deletes it: the
 * database would delete the injects with it. Runs on every delete through Hibernate; deleting a
 * tenant, in SQL, still passes.
 */
@Component
@RequiredArgsConstructor
public class PayloadDeleteGuard {

  // Provider: the repository needs the entity manager factory, which builds this listener.
  private final ObjectProvider<PayloadRepository> payloadRepository;

  @PreRemove
  void refuseIfManagedOrInUse(Payload payload) {
    if (payload.isManaged()) {
      throw CatalogPayloadReadOnlyException.onDelete();
    }
    List<String> usages =
        payloadRepository
            .getObject()
            .findUsageNamesByPayloadId(payload.getId(), PayloadInUseException.READ);
    if (!usages.isEmpty()) {
      throw new PayloadInUseException(usages);
    }
  }
}
