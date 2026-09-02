package io.openaev.api.markings;

import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.multitenancy.DependenciesManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives every new tenant the standard TLP and PAP scales, so a fresh tenant has a usable marking
 * vocabulary without an administrator seeding one by hand.
 *
 * <p>Existing tenants were seeded by the {@code V6_20260825140000000__Add_marking_definitions}
 * migration. The two lists must stay in step; the migration is the source of truth for the defaults
 * and this class mirrors it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MarkingDefinitionDependenciesManager implements DependenciesManager {

  /** name, type, order, colour — mirrors the migration's seed. */
  private static final List<Default> DEFAULTS =
      List.of(
          new Default("TLP:CLEAR", MarkingDefinition.TYPE_TLP, 10, "#ffffff"),
          new Default("TLP:GREEN", MarkingDefinition.TYPE_TLP, 20, "#2e7d32"),
          new Default("TLP:AMBER", MarkingDefinition.TYPE_TLP, 30, "#d84315"),
          new Default("TLP:AMBER+STRICT", MarkingDefinition.TYPE_TLP, 40, "#d84315"),
          new Default("TLP:RED", MarkingDefinition.TYPE_TLP, 50, "#c62828"),
          new Default("PAP:CLEAR", MarkingDefinition.TYPE_PAP, 10, "#ffffff"),
          new Default("PAP:GREEN", MarkingDefinition.TYPE_PAP, 20, "#2e7d32"),
          new Default("PAP:AMBER", MarkingDefinition.TYPE_PAP, 30, "#d84315"),
          new Default("PAP:RED", MarkingDefinition.TYPE_PAP, 50, "#c62828"));

  private final MarkingDefinitionRepository markingDefinitionRepository;

  @Override
  public void createDependencyForTenant(Tenant tenant) {
    List<MarkingDefinition> markings =
        DEFAULTS.stream().map(marking -> marking.toEntity(tenant)).toList();
    markingDefinitionRepository.saveAll(markings);
    log.info(
        "Seeded {} default marking definitions for tenant '{}'", markings.size(), tenant.getId());
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // marking_definitions rows are cascade-deleted through the tenant_id foreign key.
  }

  private record Default(String name, String type, int order, String color) {

    MarkingDefinition toEntity(Tenant tenant) {
      MarkingDefinition marking = new MarkingDefinition();
      marking.setName(name);
      marking.setType(type);
      marking.setOrder(order);
      marking.setColor(color);
      marking.setTenant(tenant);
      return marking;
    }
  }
}
