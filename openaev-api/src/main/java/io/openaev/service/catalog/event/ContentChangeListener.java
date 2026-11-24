package io.openaev.service.catalog.event;

import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.database.model.payload.PayloadRevocationChanged;
import io.openaev.database.repository.ContentChangeRepository;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.UserService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records every catalogue change in the history, in the transaction that makes it. */
@Component
@RequiredArgsConstructor
public class ContentChangeListener {

  private final ContentChangeRepository contentChangeRepository;
  private final EntityManager entityManager;
  private final PayloadService payloadService;
  private final UserService userService;

  @EventListener
  public void onContentChanged(ContentChanged event) {
    ContentChange history = new ContentChange();
    history.setTenant(entityManager.getReference(Tenant.class, event.tenantId()));
    history.setType(event.type());
    history.setRepository(event.repository());
    history.setExternalId(event.externalId());
    history.setContentId(event.contentId());
    history.setVersion(event.version());
    history.setAction(event.action());
    history.setMode(event.mode());
    if (event.userId() != null) {
      history.setUser(entityManager.getReference(User.class, event.userId()));
    }
    contentChangeRepository.save(history);
  }

  @EventListener
  public void onRevocationChanged(PayloadRevocationChanged event) {
    Payload payload = payloadService.payload(event.tenantId(), event.payloadId());
    if (payload.getCatalogRepository() == null) {
      return;
    }
    onContentChanged(
        ContentChanged.of(
            payload,
            event.revoked() ? ContentChange.Action.REVOKE : ContentChange.Action.LIFT_REVOCATION,
            event.byCatalogue() ? ContentChange.Mode.AUTO : ContentChange.Mode.MANUAL,
            userService.currentUserOrNull()));
  }
}
