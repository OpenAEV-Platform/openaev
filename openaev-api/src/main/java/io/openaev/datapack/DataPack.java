package io.openaev.datapack;

import io.openaev.database.model.Tenant;
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

  protected abstract boolean doProcess();

  @Getter private final String packId = this.getClass().getCanonicalName();

  @Transactional(rollbackFor = Exception.class)
  public DataPackProcessingResult process(Tenant tenant) {
    return dataPackService
        .findByIdAndTenant(packId, tenant)
        .map(
            dataPack -> {
              log.debug("Already processed datapack '{}' for tenant {}.", packId, tenant.getId());
              return DataPackProcessingResult.SKIPPED;
            })
        .orElseGet(
            () -> {
              log.info(
                  "Processing datapack '{}' for tenant {}.",
                  this.getClass().getCanonicalName(),
                  tenant.getId());
              if (doProcess()) {
                dataPackService.registerDataPack(packId, tenant);
              }
              return DataPackProcessingResult.PROCESSED;
            });
  }
}
