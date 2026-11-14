package io.openaev.database.model.payload;

import io.openaev.database.model.InjectorContract;
import jakarta.persistence.PreRemove;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Refuses deleting an action whose payload injects run, before its delete is queued: past that
 * point, the payload check would flush it and the database would delete the injects with it.
 */
@Component
@RequiredArgsConstructor
public class InjectorContractDeleteGuard {

  // Provider: the guard needs the entity manager factory, which builds this listener.
  private final ObjectProvider<PayloadDeleteGuard> payloadDeleteGuard;

  @PreRemove
  void refuseIfPayloadManagedOrInUse(InjectorContract contract) {
    if (contract.getPayload() != null) {
      payloadDeleteGuard.getObject().refuseIfManagedOrInUse(contract.getPayload());
    }
  }
}
