package io.openaev.processor.datapack;

import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.service.DataPackService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V20260914_Default_tenant_markings extends DataPack {

  private final MarkingDefinitionRepository markingDefinitionRepository;
  @PersistenceContext private EntityManager entityManager;

  public V20260914_Default_tenant_markings(
      DataPackService dataPackService, MarkingDefinitionRepository markingDefinitionRepository) {
    super(dataPackService);
    this.markingDefinitionRepository = markingDefinitionRepository;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    try {
      PresetTenantData.createDefaultMarkings()
          .forEach(seed -> createMarkingDefinitionIfMissing(tenant, seed));
      return true;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20260826 initialization.", e);
      return false;
    }
  }

  private void createMarkingDefinitionIfMissing(Tenant tenant, PresetTenantData.MarkingSeed seed) {
    // Find-or-create keyed by (tenant, type, definition): makes the seed idempotent so a retry
    // after a previous partial failure (some seeds already inserted, the run then crashed before
    // the pack got registered as processed) converges instead of tripping the unique index again.
    if (markingDefinitionRepository.existsByTypeAndDefinitionAndTenantIdExcludingId(
        seed.type(), seed.definition(), tenant.getId(), null)) {
      log.info(
          "Marking definition {}:{} already exists for tenant {}, skipping",
          seed.type(),
          seed.definition(),
          tenant.getId());
      return;
    }
    MarkingDefinition markingDefinition = new MarkingDefinition();
    markingDefinition.setType(seed.type());
    markingDefinition.setDefinition(seed.definition());
    markingDefinition.setColor(seed.color());
    markingDefinition.setOrder(seed.order());
    markingDefinition.setProtectedDefinition(true);
    markingDefinition.setTenant(entityManager.getReference(Tenant.class, tenant.getId()));
    markingDefinitionRepository.save(markingDefinition);
  }
}
