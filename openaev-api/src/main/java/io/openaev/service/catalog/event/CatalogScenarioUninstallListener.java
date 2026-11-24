package io.openaev.service.catalog.event;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;

import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Deleting a catalogue scenario uninstalls it, whichever path deletes it. */
@Component
@RequiredArgsConstructor
public class CatalogScenarioUninstallListener {

  private final CatalogChangePublisher changePublisher;

  @EventListener(condition = "#event.type == '" + DATA_DELETE + "'")
  public void onDelete(BaseEvent event) {
    if (event.getInstance() instanceof Scenario scenario && scenario.getCatalogIdentity() != null) {
      changePublisher.changed(
          scenario, CatalogHistory.Action.UNINSTALL, CatalogHistory.Mode.MANUAL);
    }
  }
}
