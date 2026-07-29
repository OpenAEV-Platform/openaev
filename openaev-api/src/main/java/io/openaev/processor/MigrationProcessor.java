package io.openaev.processor;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.processor.core.RuntimeMigration;
import io.openaev.processor.datapack.DataPack;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.injector_contract.InjectorContractService;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Processes all {@link RuntimeMigration} and {@link DataPack} instances for each active tenant at
 * startup. Migrations and datapacks are merged into a single chronologically-ordered list sorted by
 * simple class name ({@code V{YYYYMMDD}_Description} convention) and executed sequentially.
 *
 * <p>Each {@code processable.process(tenant)} call is wrapped in its own tenant-scoped background
 * transaction (via {@link TenantScopedTransaction}), opened HERE: neither {@link DataPack} nor
 * {@link RuntimeMigration} carries its own transaction/scope anymore. This is the single scope
 * boundary for the whole call — {@code doProcess()}/{@code doMigrate()} implementations inherit it
 * and must not open their own (see #7012).
 *
 * <p>Two call sites, two propagations: {@link #process()} (startup, {@code @PostConstruct}) has no
 * ambient transaction, so it opens a fresh one via {@link TenantScopedTransaction#execute}. {@link
 * #createDependencyForTenant} runs synchronously inside {@code TenantService.create()}'s active
 * transaction (required — {@code ManagerFactory} declares this class as a prerequisite and must see
 * its datapacks/migrations already applied), so it deliberately nests via {@link
 * TenantScopedTransaction#executeNew}. Both go through the exact same {@link #init} loop; only the
 * propagation choice differs.
 *
 * <p>Also implements {@link DependenciesManager} to initialize migrations/datapacks when a new
 * tenant is created at runtime.
 */
@Service
@Slf4j
@Profile("!test")
public class MigrationProcessor implements DependenciesManager {
  private final List<DataPack> packs;
  private final List<RuntimeMigration> migrations;
  private final TenantRepository tenantRepository;
  private final TenantScopedTransaction tenantTx;

  public MigrationProcessor(
      List<DataPack> packs,
      List<RuntimeMigration> migrations,
      TenantRepository tenantRepository,
      TenantScopedTransaction tenantTx) {
    this.packs = packs != null ? packs : Collections.emptyList();
    this.migrations = migrations != null ? migrations : Collections.emptyList();
    this.tenantRepository = tenantRepository;
    this.tenantTx = tenantTx;
  }

  @PostConstruct
  public void process() {
    // Check all active tenants to add a Datapack migration if one is added
    init(tenantRepository.findAllByDeletedAtIsNull());
  }

  // TODO: API v2 https://github.com/OpenAEV-Platform/openaev/issues/7012 - here we address only part of the proposed refactor
  private void init(List<Tenant> tenants) {
    // Merge migrations and datapacks into a single chronologically-ordered list.
    // Both follow the V{YYYYMMDD}_Description naming convention, so sorting by
    // simple class name (getSortKey) guarantees correct execution order regardless of package.
    List<Processable> allProcessables =
        Stream.<Processable>concat(migrations.stream(), packs.stream())
            .sorted(Comparator.comparing(Processable::getSortKey))
            .toList();
    for (Tenant tenant : tenants) {
      // Kept for code that still reads the v1 thread-local directly (e.g. a datapack's own
      // business-logic checks); it no longer carries any DB-scoping responsibility.
      TenantContext.setCurrentTenant(tenant.getId());
      TxCtx ctx = TxCtx.forTenant(tenant.getId());
      // At startup, no transaction is active yet: execute() opens the single top-level one. At
      // onboarding, TenantService.create()'s transaction is already active: executeNew() nests
      // deliberately, since ManagerFactory (and others) require this to have already run.
      boolean nested = TransactionSynchronizationManager.isActualTransactionActive();
      long processed =
          allProcessables.stream()
              .filter(
                  processable ->
                      MigrationProcessingResult.PROCESSED.equals(
                          nested
                              ? tenantTx.executeNew(ctx, () -> processable.process(tenant)) // call from create new tenant endpoint
                              : tenantTx.execute(ctx, () -> processable.process(tenant))))
              .count();
      log.info("Tenant {}: processed {} migrations/datapacks.", tenant.getId(), processed);
    }
  }

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    log.info("Tenant {} created — migrations and datapacks init", tenant.getId());
    try {
      // Do NOT cleanup TenantContext here — subsequent DependenciesManagers (e.g. ManagerFactory)
      // rely on TenantContext being set to the new tenant after this method returns.
      init(List.of(tenant));
    } catch (Exception e) {
      throw new DependenciesManagerException(
          "Failed to process migrations for tenant " + tenant.getId(), e);
    }
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    log.info("Deleting all migration data for tenant {}.", tenantId);
  }

  @Override
  public List<Class<? extends DependenciesManager>> getPrerequisite() {
    // We want to process datapack after all the default domain are created for the tenant
    return List.of(DomainService.class, InjectorContractService.class);
  }
}
