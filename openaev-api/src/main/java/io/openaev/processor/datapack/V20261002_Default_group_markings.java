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
import java.util.Map;
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
 * V{YYYYMMDD}_Description} naming convention guarantees this ordering. Still, {@link
 * #doProcess(Tenant)} only returns {@code true} (and so only lets {@link DataPack} register the
 * pack as processed) once every group actually got its marking: if either prerequisite pack failed
 * or hasn't run yet for this tenant, the lookups below come back empty and this pack must stay
 * unregistered so it retries on the next pass, instead of registering a half-done state that will
 * never be revisited.
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
      boolean allAssigned = true;
      for (Map.Entry<String, String> entry : PresetTenantData.DEFAULT_GROUP_MARKINGS.entrySet()) {
        // Non-short-circuiting &: every group must be attempted on every pass, even if an earlier
        // one in this same run turned out not to be ready yet.
        allAssigned &= assignMarkingToGroup(tenant, entry.getKey(), entry.getValue());
      }
      return allAssigned;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20261002 initialization.", e);
      return false;
    }
  }

  /**
   * @return {@code true} once this group/marking pair is in its desired end state (assigned, or
   *     already was); {@code false} if a prerequisite datapack hasn't produced the group or
   *     marking yet, so the caller must not let this pack register as processed.
   */
  private boolean assignMarkingToGroup(Tenant tenant, String groupName, String markingDefinition) {
    Optional<Group> maybeGroup = groupRepository.findByNameAndTenantId(groupName, tenant.getId());
    if (maybeGroup.isEmpty()) {
      if (Tenant.DEFAULT_TENANT_UUID.equals(tenant.getId())) {
        // V20260330_Default_tenant_data deliberately never creates Admin/Manager/Observer groups
        // for the default tenant, so there is nothing to grant here — this is the expected end
        // state, not a prerequisite we're waiting on.
        log.info(
            "Default tenant has no {} group by design, skipping default marking assignment",
            groupName);
        return true;
      }
      log.warn(
          "Group {} not found for tenant {}; V20260330_Default_tenant_data hasn't run yet,"
              + " deferring default marking assignment",
          groupName,
          tenant.getId());
      return false;
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
      return true;
    }

    Optional<MarkingDefinition> maybeMarking =
        markingDefinitionRepository.findByTypeAndDefinitionAndTenantId(
            MarkingDefinition.TYPE_TLP, markingDefinition, tenant.getId());
    if (maybeMarking.isEmpty()) {
      log.warn(
          "Marking definition {} not found for tenant {}; V20260914_Default_tenant_markings hasn't"
              + " run yet, deferring default marking assignment for group {}",
          markingDefinition,
          tenant.getId(),
          groupName);
      return false;
    }

    // Must be a mutable list: Hibernate needs to write into this @ManyToMany collection's backing
    // list at flush time, which an immutable List.of(...) rejects with
    // UnsupportedOperationException
    // (same reason V20260330_Default_tenant_data wraps its CWE list the same way).
    group.setMarkings(new ArrayList<>(List.of(maybeMarking.get())));
    groupRepository.save(group);
    markingClearanceCacheManager.evictForUsers(group.getUsers().stream().map(User::getId).toList());
    return true;
  }
}
