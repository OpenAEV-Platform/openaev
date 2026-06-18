package io.openaev.processor.core;

import io.openaev.database.model.Tenant;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.processor.Processable;
import io.openaev.service.DataPackService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
/**
 * Base class for one-shot tenant-scoped data migrations. Subclasses implement {@link #doMigrate()}
 * which is executed exactly once per tenant (idempotency tracked via {@link DataPackService}).
 *
 * <p>Implementations must follow the {@code V{YYYYMMDD}_Description} naming convention to ensure
 * correct chronological ordering when mixed with {@link io.openaev.processor.datapack.DataPack
 * DataPack} instances.
 */
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
