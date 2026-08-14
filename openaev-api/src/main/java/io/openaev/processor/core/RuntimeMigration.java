package io.openaev.processor.core;

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
 * Base class for one-shot tenant-scoped data migrations. Subclasses implement {@link #doMigrate()}
 * which is executed exactly once per tenant (idempotency tracked via {@link DataPackService}).
 *
 * <p>Implementations must follow the {@code V{YYYYMMDD}_Description} naming convention to ensure
 * correct chronological ordering when mixed with {@link io.openaev.processor.datapack.DataPack
 * DataPack} instances.
 *
 * <p>Deliberately NOT {@code @Transactional}: this class is background code, driven by {@link
 * io.openaev.processor.MigrationProcessor MigrationProcessor}, which opens the single tenant-scoped
 * transaction (via {@code TenantScopedTransaction.execute}) around the whole {@link
 * #process(Tenant)} call, idempotency check included. A subclass's {@link #doMigrate()} must NOT
 * open its own transaction/scope — it runs inside the caller's transaction and inherits its scope
 * automatically.
 */
public abstract class RuntimeMigration implements Processable {
  private final DataPackService dataPackService;

  @PersistenceContext private EntityManager entityManager;

  protected RuntimeMigration(DataPackService dataPackService) {
    this.dataPackService = dataPackService;
  }

  protected abstract boolean doMigrate();

  /**
   * Enables the v1 Hibernate {@code tenantFilter} for the current tenant. Call this explicitly at
   * the top of {@link #doMigrate()} when the migration reads v1-scoped entities through JPQL /
   * derived queries that are not explicitly tenant-parameterized (e.g. {@code
   * InjectorContractRepository#findById(String)} on the composite-PK {@code injectors_contracts}
   * table). Before migrations moved under {@code MigrationProcessor}'s tenant-scoped transactions,
   * the {@code @Transactional} annotation on {@code process()} made {@code
   * HibernateFilterTransactionAspect} enable this filter implicitly; the programmatic transaction
   * does not, so migrations that depend on it must opt in.
   */
  // TODO v2: once all v1 tables read by runtime migrations are v2 activated, remove this method
  // and all calls to it (and the v1 filter itself) - the SQL rewriter scopes them independently
  protected void enableV1TenantFilter() {
    entityManager
        .unwrap(Session.class)
        .enableFilter("tenantFilter")
        .setParameter("tenantId", TenantContext.getCurrentTenant());
  }

  @Getter private final String migrationId = getProcessableId();

  @Override
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
