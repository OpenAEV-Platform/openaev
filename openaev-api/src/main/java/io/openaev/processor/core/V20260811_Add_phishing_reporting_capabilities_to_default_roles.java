package io.openaev.processor.core;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import io.openaev.service.DataPackService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * One-shot migration adding the Phishing and Reporting capabilities to the auto-generated
 * Manager/Observer tenant roles (issue #7320).
 *
 * <p>{@code PresetTenantData.DEFAULT_ROLES} was never updated when the {@code *_REPORTINGS} and
 * {@code *_PHISHING} capability groups were introduced, so every tenant created since then got
 * Manager/Observer roles silently missing them. The preset is fixed for new tenants; this migration
 * heals the tenants that were already created.
 *
 * <p>Both roles are user-editable after creation, so the heal is deliberately conservative: a role
 * is only updated when its capability set still EXACTLY matches what the buggy preset generated
 * (the pre-fix defaults, parents resolved). A role an admin has customized in any way no longer
 * matches and is left untouched — silently re-granting capabilities on a deliberately restricted
 * role would be a privilege-escalation surprise, not a fix.
 */
@Component
@Slf4j
public class V20260811_Add_phishing_reporting_capabilities_to_default_roles
    extends RuntimeMigration {

  private static final String OBSERVER = "Observer";
  private static final String MANAGER = "Manager";

  /**
   * Frozen snapshot of the pre-fix {@code PresetTenantData.DEFAULT_ROLES} sets (before parents
   * resolution). Deliberately NOT referencing the live constant: the preset keeps evolving, while
   * this migration must forever recognize exactly what the buggy versions generated.
   */
  private static final Set<Capability> PRE_FIX_OBSERVER =
      Set.of(
          Capability.ACCESS_ASSESSMENT,
          Capability.ACCESS_ASSETS,
          Capability.ACCESS_THREAT_ARSENALS,
          Capability.ACCESS_DASHBOARDS,
          Capability.ACCESS_FINDINGS,
          Capability.ACCESS_DOCUMENTS,
          Capability.ACCESS_CHANNELS,
          Capability.ACCESS_CHALLENGES,
          Capability.ACCESS_LESSONS_LEARNED,
          Capability.ACCESS_SECURITY_PLATFORMS);

  private static final Set<Capability> PRE_FIX_MANAGER =
      Set.of(
          Capability.ACCESS_ASSESSMENT,
          Capability.MANAGE_ASSESSMENT,
          Capability.DELETE_ASSESSMENT,
          Capability.LAUNCH_ASSESSMENT,
          Capability.MANAGE_TEAMS_AND_PLAYERS,
          Capability.DELETE_TEAMS_AND_PLAYERS,
          Capability.ACCESS_ASSETS,
          Capability.MANAGE_ASSETS,
          Capability.DELETE_ASSETS,
          Capability.ACCESS_THREAT_ARSENALS,
          Capability.MANAGE_THREAT_ARSENALS,
          Capability.DELETE_THREAT_ARSENALS,
          Capability.ACCESS_DASHBOARDS,
          Capability.MANAGE_DASHBOARDS,
          Capability.DELETE_DASHBOARDS,
          Capability.ACCESS_FINDINGS,
          Capability.MANAGE_FINDINGS,
          Capability.DELETE_FINDINGS,
          Capability.ACCESS_DOCUMENTS,
          Capability.MANAGE_DOCUMENTS,
          Capability.DELETE_DOCUMENTS,
          Capability.ACCESS_CHANNELS,
          Capability.MANAGE_CHANNELS,
          Capability.DELETE_CHANNELS,
          Capability.ACCESS_CHALLENGES,
          Capability.MANAGE_CHALLENGES,
          Capability.DELETE_CHALLENGES,
          Capability.ACCESS_LESSONS_LEARNED,
          Capability.MANAGE_LESSONS_LEARNED,
          Capability.DELETE_LESSONS_LEARNED,
          Capability.ACCESS_SECURITY_PLATFORMS,
          Capability.DELETE_SECURITY_PLATFORMS,
          Capability.MANAGE_SECURITY_PLATFORMS);

  private static final Set<Capability> OBSERVER_ADDITIONS =
      Set.of(Capability.ACCESS_REPORTINGS, Capability.ACCESS_PHISHING);

  private static final Set<Capability> MANAGER_ADDITIONS =
      Set.of(
          Capability.ACCESS_REPORTINGS,
          Capability.MANAGE_REPORTINGS,
          Capability.DELETE_REPORTINGS,
          Capability.ACCESS_PHISHING,
          Capability.MANAGE_PHISHING,
          Capability.DELETE_PHISHING);

  private final RoleRepository roleRepository;

  public V20260811_Add_phishing_reporting_capabilities_to_default_roles(
      DataPackService dataPackService, RoleRepository roleRepository) {
    super(dataPackService);
    this.roleRepository = roleRepository;
  }

  @Override
  protected boolean doMigrate() {
    String tenantId = TenantContext.getCurrentTenant();
    Map<String, Set<Capability>> preFixByRole =
        Map.of(OBSERVER, PRE_FIX_OBSERVER, MANAGER, PRE_FIX_MANAGER);
    Map<String, Set<Capability>> additionsByRole =
        Map.of(OBSERVER, OBSERVER_ADDITIONS, MANAGER, MANAGER_ADDITIONS);

    for (Role role : roleRepository.findAllByTenantId(tenantId)) {
      Set<Capability> preFix = preFixByRole.get(role.getName());
      if (preFix == null) {
        continue;
      }
      // Already healed (tenant created with the fixed preset, or capabilities granted manually):
      // nothing to do. This also keeps the log quiet for every new tenant, whose creation runs the
      // fixed V20260330 datapack right before this migration.
      if (role.getCapabilities().containsAll(additionsByRole.get(role.getName()))) {
        continue;
      }
      // The roles are stored with parents resolved (RoleService#createRoleInternal), so the
      // comparison must be against the resolved pre-fix set.
      Set<Capability> expected = Capability.resolveWithParents(preFix);
      if (!expected.equals(role.getCapabilities())) {
        log.info(
            "Role '{}' ({}) of tenant {} no longer matches the auto-generated defaults —"
                + " leaving it untouched.",
            role.getName(),
            role.getId(),
            tenantId);
        continue;
      }
      Set<Capability> healed = new HashSet<>(expected);
      healed.addAll(additionsByRole.get(role.getName()));
      role.setCapabilities(Capability.resolveWithParents(healed));
      roleRepository.save(role);
      log.info(
          "Added Phishing/Reporting capabilities to auto-generated role '{}' ({}) of tenant {}.",
          role.getName(),
          role.getId(),
          tenantId);
    }
    return true;
  }
}
