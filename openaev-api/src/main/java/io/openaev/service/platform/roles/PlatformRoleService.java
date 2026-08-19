package io.openaev.service.platform.roles;

import static io.openaev.database.specification.RoleSpecification.platformScope;
import static io.openaev.service.account.PrivilegeEscalationValidator.assertCanAssignCapabilities;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityScope;
import io.openaev.database.model.Role;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(rollbackFor = Exception.class)
public class PlatformRoleService {

  private final RoleRepository roleRepository;
  private final GroupRepository groupRepository;
  private final UserService userService;

  // -- CREATE --

  public Role createPlatformRole(
      @NotBlank final String name,
      final String description,
      @NotNull final Set<Capability> capabilities) {
    assertCanAssignCapabilities(userService.currentUser(), capabilities, CapabilityScope.PLATFORM);
    Capability.validateForPlatformRole(capabilities);
    Role role = new Role();
    role.setName(name);
    role.setDescription(description);
    role.setCapabilities(capabilities);
    return roleRepository.save(role);
  }

  /**
   * Idempotently creates or updates a system-managed platform role under a fixed, well-known id
   * (used by the bootstrap to seed the platform administrators role). This is an internal seeding
   * path that keeps the role platform-scoped (no tenant); unlike the tenant-scoped role CRUD, it
   * does not run the reserved-id validation. Fails fast if a role already exists under the id but
   * is tenant-scoped, since {@code tenant_id} is {@code updatable=false} and silently reusing it
   * would grant incorrect, tenant-scoped privileges.
   */
  public Role ensureInternalPlatformRole(
      @NotBlank final String id,
      @NotBlank final String name,
      final String description,
      @NotNull final Set<Capability> capabilities) {
    Capability.validateForPlatformRole(capabilities);
    Role role = roleRepository.findById(id).orElseGet(Role::new);
    if (role.getTenant() != null) {
      throw new IllegalStateException(
          "Role " + id + " already exists as a tenant-scoped role; expected a platform role");
    }
    role.setId(id);
    role.setName(name);
    role.setDescription(description);
    role.setCapabilities(capabilities);
    return roleRepository.save(role);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Role findById(String id) {
    return roleRepository
        .findById(id)
        .filter(role -> role.getTenant() == null)
        .orElseThrow(() -> new EntityNotFoundException("Platform role not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<Role> findByIds(List<String> ids) {
    return roleRepository.findAllById(ids).stream()
        .filter(role -> role.getTenant() == null)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<Role> search(@NotNull SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<Role> spec, org.springframework.data.domain.Pageable pageable) ->
            roleRepository.findAll(platformScope().and(spec), pageable),
        searchPaginationInput,
        Role.class);
  }

  // -- UPDATE --

  public Role updatePlatformRole(
      @NotBlank final String roleId,
      @NotBlank final String name,
      final String description,
      @NotNull final Set<Capability> capabilities) {
    Set<Capability> scopedCapabilities = Capability.filterForPlatformRole(capabilities);
    Role role = findById(roleId);
    Set<Capability> currentScopedCapabilities =
        Capability.filterForPlatformRole(role.getCapabilities());
    Set<Capability> capabilitiesToAuthorize = new HashSet<>(currentScopedCapabilities);
    capabilitiesToAuthorize.addAll(scopedCapabilities);
    assertCanAssignCapabilities(
        userService.currentUser(), capabilitiesToAuthorize, CapabilityScope.PLATFORM);
    role.setName(name);
    role.setDescription(description);
    role.setCapabilities(scopedCapabilities);
    return roleRepository.save(role);
  }

  // -- DELETE --

  public void delete(@NotBlank final String roleId) {
    Role role = findById(roleId);
    groupRepository.findAllByRoles(role).forEach(group -> group.getRoles().remove(role));
    roleRepository.delete(role);
  }
}
