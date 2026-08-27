package io.openaev.processor.datapack;

import io.openaev.context.TenantContext;
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
public class V20260826_Default_tenant_markings extends DataPack {

  private final MarkingDefinitionRepository markingDefinitionRepository;
  @PersistenceContext private EntityManager entityManager;

  public V20260826_Default_tenant_markings(
      DataPackService dataPackService, MarkingDefinitionRepository markingDefinitionRepository) {
    super(dataPackService);
    this.markingDefinitionRepository = markingDefinitionRepository;
  }

  @Override
  public boolean doProcess() {
    try {
      PresetTenantData.createDefaultMarkings()
          .forEach(
              seed -> {
                MarkingDefinition markingDefinition = new MarkingDefinition();
                markingDefinition.setType(seed.type());
                markingDefinition.setDefinition(seed.definition());
                markingDefinition.setColor(seed.color());
                markingDefinition.setOrder(seed.order());
                markingDefinition.setProtectedDefinition(true);
                markingDefinition.setTenant(
                    entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
                markingDefinitionRepository.save(markingDefinition);
              });
      return true;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20260826 initialization.", e);
      return false;
    }
  }
}
