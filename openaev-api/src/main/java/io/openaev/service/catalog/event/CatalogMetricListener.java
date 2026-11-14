package io.openaev.service.catalog.event;

import io.openaev.telemetry.metric_collectors.ContentMetricCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Counts every catalogue change once it is committed. */
@Component
@RequiredArgsConstructor
public class CatalogMetricListener {

  private final ContentMetricCollector contentMetricCollector;

  @TransactionalEventListener
  public void onContentChanged(CatalogContentChanged event) {
    contentMetricCollector.addAction(event.type(), event.action());
  }
}
