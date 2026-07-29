package io.openaev.processor.datapack;

import io.openaev.database.model.Tenant;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.processor.Processable;
import io.openaev.service.DataPackService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * Base class for tenant-scoped data packs (initial/seed data). Subclasses implement {@link
 * #doProcess()} which is executed exactly once per tenant (idempotency tracked via {@link
 * DataPackService}).
 *
 * <p>Implementations must follow the {@code V{YYYYMMDD}_Description} naming convention to ensure
 * correct chronological ordering when mixed with {@link io.openaev.processor.core.RuntimeMigration
 * RuntimeMigration} instances.
 *
 * <p>Deliberately NOT {@code @Transactional}: this class is background code, driven by {@link
 * io.openaev.processor.MigrationProcessor MigrationProcessor}, which opens the single tenant-scoped
 * transaction (via {@code TenantScopedTransaction.execute}) around the whole {@link
 * #process(Tenant)} call, idempotency check included. A subclass's {@link #doProcess()} must NOT
 * open its own transaction/scope (no {@code @Transactional}, no {@code TenantScopedTransaction}
 * call) — it runs inside the caller's transaction and inherits its scope automatically.
 */
public abstract class DataPack implements Processable {
  private final DataPackService dataPackService;

  protected DataPack(DataPackService dataPackService) {
    this.dataPackService = dataPackService;
  }

  protected abstract boolean doProcess();

  @Getter private final String packId = getProcessableId();

  @Override
  public MigrationProcessingResult process(Tenant tenant) {
    return dataPackService
        .findByIdAndTenant(packId, tenant)
        .map(
            dataPack -> {
              log.debug("Already processed datapack '{}' for tenant {}.", packId, tenant.getId());
              return MigrationProcessingResult.SKIPPED;
            })
        .orElseGet(
            () -> {
              log.info("Processing datapack '{}' for tenant {}.", packId, tenant.getId());
              if (doProcess()) {
                dataPackService.registerDataPack(packId, tenant);
              }
              return MigrationProcessingResult.PROCESSED;
            });
  }
}
