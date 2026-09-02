package io.openaev.processor.datapack;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.processor.Processable;
import io.openaev.service.DataPackService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;

@Slf4j
/**
 * Base class for tenant-scoped data packs (initial/seed data). Subclasses implement {@link
 * #doProcess(Tenant)} which is executed exactly once per tenant (idempotency tracked via {@link
 * DataPackService}).
 *
 * <p>Implementations must follow the {@code V{YYYYMMDD}_Description} naming convention to ensure
 * correct chronological ordering when mixed with {@link io.openaev.processor.core.RuntimeMigration
 * RuntimeMigration} instances.
 *
 * <p>Deliberately NOT {@code @Transactional}: this class is background code, driven by {@link
 * io.openaev.processor.MigrationProcessor MigrationProcessor}, which opens the single tenant-scoped
 * transaction (via {@code TenantScopedTransaction.execute}) around the whole {@link
 * #process(Tenant)} call, idempotency check included. A subclass's {@link #doProcess(Tenant)} must
 * NOT open its own transaction/scope (no {@code @Transactional}, no {@code TenantScopedTransaction}
 * call) — it runs inside the caller's transaction and inherits its scope automatically.
 */
public abstract class DataPack implements Processable {
  private final DataPackService dataPackService;

  @PersistenceContext private EntityManager entityManager;

  protected DataPack(DataPackService dataPackService) {
    this.dataPackService = dataPackService;
  }

  protected abstract boolean doProcess(Tenant tenant);

  /**
   * Enables the v1 Hibernate {@code tenantFilter} for the current tenant. Call this explicitly,
   * when doing a datapack that touches v1 tables. Once the tables are migrated to v2, this call can
   * be removed.
   */
  // TODO v2: once tags, tags_rules get v2 activated, remove this method and all calls to it (and
  // the v1 filter itself)
  protected void enableV1TenantFilter() {
    entityManager
        .unwrap(Session.class)
        .enableFilter("tenantFilter")
        .setParameter("tenantId", TenantContext.getCurrentTenant());
  }

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
              if (doProcess(tenant)) {
                dataPackService.registerDataPack(packId, tenant);
              }
              return MigrationProcessingResult.PROCESSED;
            });
  }
}
