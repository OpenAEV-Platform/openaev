package io.openaev.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;

import io.openaev.database.audit.IndexEvent;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EngineListener {

  private EngineService esService;

  @Autowired
  public void setEsService(EngineService esService) {
    this.esService = esService;
  }

  @EventListener
  public void listenIndexEvent(IndexEvent event) {
    if (Objects.equals(event.getType(), DATA_DELETE)) {
      // TODO Combine through time to generate a real bulk delete
      this.esService.bulkDelete(List.of(event.getId()));
    }
  }
}
