package io.openaev.datapack;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.processor.MigrationProcessor;
import io.openaev.processor.datapack.V20261002_Default_group_markings;
import io.openaev.service.DataPackService;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Deliberately NOT {@code @Transactional}: same reasoning as {@link MigrationProcessorTest} —
 * {@link MigrationProcessor} opens its own tenant-scoped background transaction per {@code (tenant,
 * processable)} via {@link TenantScopedTransaction#execute}, which refuses to run inside an
 * already-active transaction. Seed and clean up through committed writes instead.
 */
class V20261002DefaultGroupMarkingsTest extends IntegrationTest {

  @Autowired private DataPackService dataPackService;
  @Autowired private V20261002_Default_group_markings groupMarkingsDataPack;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private MarkingDefinitionRepository markingDefinitionRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EntityManager entityManager;

  @AfterEach
  void cleanup() {
    Tenant tenant = new Tenant(TenantContext.getCurrentTenant());
    tenantTx.execute(
        TxCtx.forTenant(tenant.getId()),
        () -> {
          entityManager
              .createNativeQuery(
                  "DELETE FROM groups_markings WHERE group_id IN"
                      + " (SELECT group_id FROM groups WHERE tenant_id = ?1)")
              .setParameter(1, tenant.getId())
              .executeUpdate();
          entityManager
              .createNativeQuery("DELETE FROM groups WHERE tenant_id = ?1")
              .setParameter(1, tenant.getId())
              .executeUpdate();
          entityManager
              .createNativeQuery("DELETE FROM marking_definitions WHERE tenant_id = ?1")
              .setParameter(1, tenant.getId())
              .executeUpdate();
          entityManager
              .createNativeQuery("DELETE FROM datapacks WHERE datapack_id = ?1 AND tenant_id = ?2")
              .setParameter(1, groupMarkingsDataPack.getPackId())
              .setParameter(2, tenant.getId())
              .executeUpdate();
          return null;
        });
  }

  @Test
  @DisplayName("given_defaultGroupsAndMarkingsExist_should_grantEachGroupItsConfiguredMarking")
  void given_defaultGroupsAndMarkingsExist_should_grantEachGroupItsConfiguredMarking()
      throws Exception {
    Tenant tenant = new Tenant(TenantContext.getCurrentTenant());

    // Arrange: groups and marking definitions as they would exist after
    // V20260330_Default_tenant_data and V20260914_Default_tenant_markings have already run.
    tenantTx.execute(
        TxCtx.forTenant(tenant.getId()),
        () -> {
          seedGroup(tenant, "Admin");
          seedGroup(tenant, "Manager");
          seedGroup(tenant, "Observer");
          seedMarking(tenant, "TLP:RED", 5);
          seedMarking(tenant, "TLP:AMBER", 3);
          seedMarking(tenant, "TLP:GREEN", 2);
          return null;
        });

    MigrationProcessor processor =
        new MigrationProcessor(
            List.of(groupMarkingsDataPack), Collections.emptyList(), tenantRepository, tenantTx);

    // Act
    processor.createDependencyForTenant(tenant);

    // Assert
    assertThat(dataPackService.findByIdAndTenant(groupMarkingsDataPack.getPackId(), tenant))
        .isPresent();
    assertGroupHasMarking(tenant, "Admin", "TLP:RED");
    assertGroupHasMarking(tenant, "Manager", "TLP:AMBER");
    assertGroupHasMarking(tenant, "Observer", "TLP:GREEN");
  }

  private void seedGroup(Tenant tenant, String name) {
    Group group = new Group();
    group.setName(name);
    group.setDescription(name);
    group.setDefaultUserAssignation(false);
    group.setTenant(new Tenant(tenant.getId()));
    groupRepository.save(group);
  }

  private void seedMarking(Tenant tenant, String definition, int order) {
    MarkingDefinition marking = new MarkingDefinition();
    marking.setType(MarkingDefinition.TYPE_TLP);
    marking.setDefinition(definition);
    marking.setColor("#000000");
    marking.setOrder(order);
    marking.setProtectedDefinition(true);
    marking.setTenant(new Tenant(tenant.getId()));
    markingDefinitionRepository.save(marking);
  }

  private void assertGroupHasMarking(Tenant tenant, String groupName, String definition) {
    Group group = groupRepository.findByNameAndTenantId(groupName, tenant.getId()).orElseThrow();
    assertThat(group.getMarkings())
        .extracting(MarkingDefinition::getDefinition)
        .containsExactly(definition);
  }
}
