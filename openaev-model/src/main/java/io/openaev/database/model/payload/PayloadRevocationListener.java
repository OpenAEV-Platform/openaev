package io.openaev.database.model.payload;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PreRemove;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Announces every revocation and every lift, whichever service makes it. */
@Component
@RequiredArgsConstructor
public class PayloadRevocationListener {

  private final ApplicationEventPublisher eventPublisher;

  @PostPersist
  void revoked(PayloadRevocation revocation) {
    eventPublisher.publishEvent(changed(revocation, true));
  }

  @PreRemove
  void lifted(PayloadRevocation revocation) {
    eventPublisher.publishEvent(changed(revocation, false));
  }

  private static PayloadRevocationChanged changed(PayloadRevocation revocation, boolean revoked) {
    return new PayloadRevocationChanged(
        revocation.getTenant().getId(),
        revocation.getPayload().getId(),
        revoked,
        revocation.getReason(),
        revocation.isByCatalogue());
  }
}
