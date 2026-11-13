package io.openaev.service.catalog.event;

import io.openaev.database.model.User;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Announces a catalogue change, made by the current user if any, to whoever listens. */
@Component
@RequiredArgsConstructor
public class CatalogChangePublisher {

  private final UserService userService;
  private final ApplicationEventPublisher eventPublisher;

  public void payloadChanged(
      PayloadIdentity identity, String version, CatalogHistory.Action action) {
    User user = userService.currentUserOrNull();
    eventPublisher.publishEvent(
        new CatalogContentChanged(
            identity.getTenant().getId(),
            ContentType.PAYLOAD,
            identity.getRepository(),
            identity.getExternalId(),
            version,
            action,
            user == null ? null : user.getId()));
  }
}
