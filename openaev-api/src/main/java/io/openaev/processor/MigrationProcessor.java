package io.openaev.processor;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.processor.core.JavaMigration;
import io.openaev.processor.datapack.DataPack;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.injector_contract.InjectorContractService;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("!test")
public class MigrationProcessor implements DependenciesManager {
  private final List<DataPack> packs;
  private final List<JavaMigration> migrations;
  private final TenantRepository tenantRepository;

  public MigrationProcessor(
      List<DataPack> packs, List<JavaMigration> migrations, TenantRepository tenantRepository) {
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
    List<JavaMigration> sortedMigrations =
        migrations.stream().sorted(Comparator.comparing(JavaMigration::getMigrationId)).toList();
    List<DataPack> sortedPacks =
        packs.stream().sorted(Comparator.comparing(DataPack::getPackId)).toList();
    for (Tenant tenant : tenants) {
      TenantContext.setCurrentTenant(tenant.getId());
      long migrationsProcessed =
          sortedMigrations.stream()
              .filter(
                  migration ->
                      MigrationProcessingResult.PROCESSED.equals(migration.process(tenant)))
              .count();
      long packsProcessed =
          sortedPacks.stream()
              .filter(pack -> MigrationProcessingResult.PROCESSED.equals(pack.process(tenant)))
              .count();
      log.info(
          "Tenant {}: processed {} migrations, {} datapacks.",
          tenant.getId(),
          migrationsProcessed,
          packsProcessed);
    }
  }

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    log.info("Tenant {} created — migrations and datapacks init", tenant.getId());
    try {
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
