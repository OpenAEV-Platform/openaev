package io.openaev.service.catalog.event;

import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.repository.CatalogHistoryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records every catalogue change in the history, in the transaction that makes it. */
@Component
@RequiredArgsConstructor
public class CatalogHistoryListener {

  private final CatalogHistoryRepository catalogHistoryRepository;
  private final EntityManager entityManager;

  @EventListener
  public void onContentChanged(CatalogContentChanged event) {
    CatalogHistory history = new CatalogHistory();
    history.setTenant(entityManager.getReference(Tenant.class, event.tenantId()));
    history.setType(event.type());
    history.setRepository(event.repository());
    history.setExternalId(event.externalId());
    history.setVersion(event.version());
    history.setAction(event.action());
    if (event.userId() != null) {
      history.setUser(entityManager.getReference(User.class, event.userId()));
    }
    catalogHistoryRepository.save(history);
  }
}
