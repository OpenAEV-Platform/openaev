package io.openaev.processor.datapack;

import io.openaev.database.model.Tenant;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.processor.Processable;
import io.openaev.service.DataPackService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
/**
 * Base class for tenant-scoped data packs (initial/seed data). Subclasses implement {@link
 * #doProcess()} which is executed exactly once per tenant (idempotency tracked via {@link
 * DataPackService}).
 *
 * <p>Implementations must follow the {@code V{YYYYMMDD}_Description} naming convention to ensure
 * correct chronological ordering when mixed with {@link io.openaev.processor.core.RuntimeMigration
 * RuntimeMigration} instances.
 */
public abstract class DataPack implements Processable {
  private final DataPackService dataPackService;

  protected DataPack(DataPackService dataPackService) {
    this.dataPackService = dataPackService;
  }

  /**
   * Implementations receive the tenant explicitly and must pass it down to any tenant-scoped call
   * (e.g. {@code TxCtx.forTenant(tenant.getId())} for v2-activated tables). Do not fall back to
   * {@code TenantContext.getCurrentTenant()}: it is a v1 thread-local that background code must not
   * rely on now that this tenant is passed as an explicit argument.
   */
  protected abstract boolean doProcess(Tenant tenant);

  @Getter private final String packId = getProcessableId();

  @Override
  @Transactional(rollbackFor = Exception.class)
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
              if (doProcess(tenant)) {
                dataPackService.registerDataPack(packId, tenant);
              }
              return MigrationProcessingResult.PROCESSED;
            });
  }
}
