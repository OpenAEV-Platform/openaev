package io.openaev.processor.datapack;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.service.DataPackService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Grants each default group ({@link PresetTenantData#DEFAULT_ROLES}) the TLP marking listed in
 * {@link PresetTenantData#DEFAULT_GROUP_MARKINGS} — Admin gets TLP:RED, Manager TLP:AMBER, Observer
 * TLP:GREEN — so the clearance a member gets from their group matches the access level implied by
 * their role.
 *
 * <p>Must run after {@link V20260330_Default_tenant_data} (creates the groups) and {@link
 * V20260914_Default_tenant_markings} (creates the marking definitions); the {@code
 * V{YYYYMMDD}_Description} naming convention guarantees this ordering.
 */
@Component
@Slf4j
public class V20261002_Default_group_markings extends DataPack {

  private final GroupRepository groupRepository;
  private final MarkingDefinitionRepository markingDefinitionRepository;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;

  public V20261002_Default_group_markings(
      DataPackService dataPackService,
      GroupRepository groupRepository,
      MarkingDefinitionRepository markingDefinitionRepository,
      MarkingClearanceCacheManager markingClearanceCacheManager) {
    super(dataPackService);
    this.groupRepository = groupRepository;
    this.markingDefinitionRepository = markingDefinitionRepository;
    this.markingClearanceCacheManager = markingClearanceCacheManager;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    try {
      PresetTenantData.DEFAULT_GROUP_MARKINGS.forEach(
          (groupName, markingDefinition) ->
              assignMarkingToGroup(tenant, groupName, markingDefinition));
      return true;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20261002 initialization.", e);
      return false;
    }
  }

  private void assignMarkingToGroup(Tenant tenant, String groupName, String markingDefinition) {
    Optional<Group> maybeGroup = groupRepository.findByNameAndTenantId(groupName, tenant.getId());
    if (maybeGroup.isEmpty()) {
      log.warn(
          "Group {} not found for tenant {}, skipping default marking assignment",
          groupName,
          tenant.getId());
      return;
    }
    Group group = maybeGroup.get();
    // Idempotent on retry: a prior partial run may have already set this marking before the pack
    // got registered as processed (see V20260914_Default_tenant_markings for the same pattern).
    boolean alreadyGranted =
        group.getMarkings().stream()
            .anyMatch(
                marking ->
                    MarkingDefinition.TYPE_TLP.equalsIgnoreCase(marking.getType())
                        && markingDefinition.equalsIgnoreCase(marking.getDefinition()));
    if (alreadyGranted) {
      log.info(
          "Group {} already has marking {} for tenant {}, skipping",
          groupName,
          markingDefinition,
          tenant.getId());
      return;
    }

    Optional<MarkingDefinition> maybeMarking =
        markingDefinitionRepository.findByTypeAndDefinitionAndTenantId(
            MarkingDefinition.TYPE_TLP, markingDefinition, tenant.getId());
    if (maybeMarking.isEmpty()) {
      log.warn(
          "Marking definition {} not found for tenant {}, skipping default marking assignment for"
              + " group {}",
          markingDefinition,
          tenant.getId(),
          groupName);
      return;
    }

    // Must be a mutable list: Hibernate needs to write into this @ManyToMany collection's backing
    // list at flush time, which an immutable List.of(...) rejects with
    // UnsupportedOperationException
    // (same reason V20260330_Default_tenant_data wraps its CWE list the same way).
    group.setMarkings(new ArrayList<>(List.of(maybeMarking.get())));
    groupRepository.save(group);
    markingClearanceCacheManager.evictForUsers(group.getUsers().stream().map(User::getId).toList());
  }
}
