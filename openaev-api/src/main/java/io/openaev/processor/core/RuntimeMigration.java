package io.openaev.processor.core;

import io.openaev.database.model.Tenant;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.processor.Processable;
import io.openaev.service.DataPackService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public abstract class RuntimeMigration implements Processable {
  private final DataPackService dataPackService;

  protected RuntimeMigration(DataPackService dataPackService) {
    this.dataPackService = dataPackService;
  }

  protected abstract boolean doMigrate();

  @Getter private final String migrationId = getProcessableId();

  @Override
  @Transactional(rollbackFor = Exception.class)
  public MigrationProcessingResult process(Tenant tenant) {
    return dataPackService
        .findByIdAndTenant(migrationId, tenant)
        .map(
            dataPack -> {
              log.debug(
                  "Already processed migration '{}' for tenant {}.", migrationId, tenant.getId());
              return MigrationProcessingResult.SKIPPED;
            })
        .orElseGet(
            () -> {
              log.info(
                  "Processing migration '{}' for tenant {}.",
                  this.getClass().getCanonicalName(),
                  tenant.getId());
              if (doMigrate()) {
                dataPackService.registerDataPack(migrationId, tenant);
              }
              return MigrationProcessingResult.PROCESSED;
            });
  }
}
