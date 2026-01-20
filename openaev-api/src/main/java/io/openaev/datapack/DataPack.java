package io.openaev.datapack;

import io.openaev.service.DataPackService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public abstract class DataPack {
  private final DataPackService dataPackService;

  protected DataPack(DataPackService dataPackService) {
    this.dataPackService = dataPackService;
  }

  protected abstract void doProcess();

  @Getter private final String packId = this.getClass().getCanonicalName();

  @Transactional(rollbackFor = Exception.class)
  public DataPackProcessingResult process() {
    return dataPackService
        .findById(packId)
        .map(
            dataPack -> {
              log.debug("Already processed datapack '{}'.", packId);
              return DataPackProcessingResult.SKIPPED;
            })
        .orElseGet(
            () -> {
              log.info("Processing datapack '{}'.", this.getClass().getCanonicalName());
              doProcess();
              dataPackService.registerDataPack(packId);
              return DataPackProcessingResult.PROCESSED;
            });
  }
}
