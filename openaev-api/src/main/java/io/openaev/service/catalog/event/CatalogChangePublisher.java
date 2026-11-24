package io.openaev.service.catalog.event;

import io.openaev.database.model.catalog.CatalogContent;
import io.openaev.database.model.catalog.CatalogHistory;
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

  public void changed(
      CatalogContent content, CatalogHistory.Action action, CatalogHistory.Mode mode) {
    eventPublisher.publishEvent(
        CatalogContentChanged.of(content, action, mode, userService.currentUserOrNull()));
  }
}
