package io.openaev.processor;

import io.openaev.context.TenantContext;
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

/**
 * Processes all {@link RuntimeMigration} and {@link DataPack} instances for each active tenant at
 * startup. Migrations and datapacks are merged into a single chronologically-ordered list sorted by
 * simple class name ({@code V{YYYYMMDD}_Description} convention) and executed sequentially.
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

  public MigrationProcessor(
      List<DataPack> packs, List<RuntimeMigration> migrations, TenantRepository tenantRepository) {
    this.packs = packs != null ? packs : Collections.emptyList();
    this.migrations = migrations != null ? migrations : Collections.emptyList();
    this.tenantRepository = tenantRepository;
  }

  @PostConstruct
  public void process() {
    // Check all active tenants to add a Datapack migration if one is added
    init(tenantRepository.findAllByDeletedAtIsNull());
  }

  private void init(List<Tenant> tenants) {
    // Merge migrations and datapacks into a single chronologically-ordered list.
    // Both follow the V{YYYYMMDD}_Description naming convention, so sorting by
    // simple class name (getSortKey) guarantees correct execution order regardless of package.
    List<Processable> allProcessables =
        Stream.<Processable>concat(migrations.stream(), packs.stream())
            .sorted(Comparator.comparing(Processable::getSortKey))
            .toList();
    for (Tenant tenant : tenants) {
      TenantContext.setCurrentTenant(tenant.getId());
      long processed =
          allProcessables.stream()
              .filter(
                  processable ->
                      MigrationProcessingResult.PROCESSED.equals(processable.process(tenant)))
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
