package io.openaev.datapack;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.helper.StreamHelper;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.domain.DomainService;
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!test")
public class DataPackProcessor implements DependenciesManager {
  private final List<DataPack> packs;
  private final TenantRepository tenantRepository;

  @PostConstruct
  public void process() {
    // Check all tenant to add a Datapack migration if one is added
    init(StreamHelper.fromIterable(tenantRepository.findAll()));
  }

  private void init(List<Tenant> tenants) {
    List<DataPack> sortedPacks =
        packs.stream().sorted(Comparator.comparing(DataPack::getPackId)).toList();
    for (Tenant tenant : tenants) {
      // Context platform here but executed at OpenAEV start or from the tenant creation API so
      // we can use the
      // tenant context to process the datapack with the right tenant automatically before the
      // transactional annotation in the process method
      TenantContext.setCurrentTenant(tenant.getId());
      log.info(
          "Processed {} additional datapacks for tenant {}.",
          sortedPacks.stream()
              .filter(pack -> DataPackProcessingResult.PROCESSED.equals(pack.process(tenant)))
              .count(),
          tenant.getId());
    }
  }

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    log.info("Tenant {} created — datapacks init", tenant.getId());
    try {
      init(List.of(tenant));
    } catch (Exception e) {
      throw new DependenciesManagerException(
          "Failed to process datapacks for tenant " + tenant.getId(), e);
    }
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // Automatic thanks to cascade delete
    log.info("Deleting all data from datapack and datapack itself for tenant {}.", tenantId);
  }

  @Override
  public List<Class<? extends DependenciesManager>> getPrerequisite() {
    // We want to process datapack after all the default domain are created for the tenant
    return List.of(DomainService.class);
  }
}
