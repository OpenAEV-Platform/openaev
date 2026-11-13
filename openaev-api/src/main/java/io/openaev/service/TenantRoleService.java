package io.openaev.service;

import static io.openaev.database.specification.RoleSpecification.tenantScope;
import static io.openaev.service.account.PrivilegeEscalationValidator.assertCanAssignCapabilities;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityScope;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(rollbackFor = Exception.class)
public class TenantRoleService {

  private final RoleRepository roleRepository;
  private final GroupRepository groupRepository;
  private final UserService userService;

  private PermissionService permissionService;

  @Autowired
  public void setPermissionService(@Lazy PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  // -- CREATE --

  public Role createRole(
      @NotBlank final String tenantId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    String id = UUID.randomUUID().toString();
    assertCanAssignInTenant(tenantId, capabilities);
    ReservedKeyValidator.validateRoleId(id);
    return createRoleInternal(id, roleName, roleDescription, capabilities, tenantId);
  }

  /**
   * Internal method for system-managed roles (e.g. service accounts). Bypasses reserved name
   * validation.
   */
  public Role createRoleInternal(
      @NotBlank final String id,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities,
      @NotBlank final String tenantId) {
    Capability.validateForTenantRole(capabilities);

    Role role = new Role();
    role.setId(id);
    role.setName(roleName);
    role.setDescription(roleDescription);
    role.setCapabilities(capabilities);
    role.setTenant(new Tenant(tenantId));
    return roleRepository.save(role);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Optional<Role> findByIdAndTenant(
      @NotBlank final String roleId, @NotBlank final String tenantId) {
    return roleRepository.findByIdAndTenantId(roleId, tenantId);
  }

  @Transactional(readOnly = true)
  public Role findByIdInTenants(
      @NotBlank final String roleId, @NotNull final Collection<String> tenantIds) {
    return loadInTenants(roleId, tenantIds);
  }

  private Role loadInTenants(final String roleId, final Collection<String> tenantIds) {
    return roleRepository
        .findByIdAndTenantIdIn(roleId, tenantIds)
        .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));
  }

  @Transactional(readOnly = true)
  public List<Role> findAllByIdInTenant(
      @NotBlank final String tenantId, @NotNull final Collection<String> roleIds) {
    return roleRepository.findAllByIdInAndTenantId(roleIds, tenantId);
  }

  @Transactional(readOnly = true)
  public List<Role> findAll(@NotNull final Collection<String> tenantIds) {
    return roleRepository.findAllByTenantIdIn(tenantIds);
  }

  /** Role names are not unique: oldest first, lowest id on a tie. */
  @Transactional(readOnly = true)
  public List<Role> findByNameOldestFirst(
      @NotBlank final String tenantId, @NotBlank final String name) {
    return roleRepository.findByTenantIdAndName(tenantId, name).stream()
        .sorted(Comparator.comparing(Role::getCreatedAt).thenComparing(Role::getId))
        .toList();
  }

  /** The oldest role of this name, else created with these capabilities. */
  public Role findOrCreate(
      @NotBlank final String tenantId,
      @NotBlank final String name,
      @NotNull final Set<Capability> capabilities) {
    return findByNameOldestFirst(tenantId, name).stream()
        .findFirst()
        .orElseGet(
            () ->
                createRoleInternal(
                    UUID.randomUUID().toString(), name, name, capabilities, tenantId));
  }

  @Transactional(readOnly = true)
  public Page<Role> search(
      SearchPaginationInput searchPaginationInput, @NotNull final Collection<String> tenantIds) {
    return buildPaginationJPA(
        (Specification<Role> spec, org.springframework.data.domain.Pageable pageable) ->
            roleRepository.findAll(tenantScope(tenantIds).and(spec), pageable),
        searchPaginationInput,
        Role.class);
  }

  // -- UPDATE --

  public Role updateRole(
      @NotBlank final String tenantId,
      @NotBlank final String roleId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    Set<Capability> scopedCapabilities = Capability.filterForTenantRole(capabilities);
    assertCanAssignInTenant(tenantId, scopedCapabilities);
    ReservedKeyValidator.validateRoleId(roleId);
    return updateRoleInternal(roleId, roleName, roleDescription, scopedCapabilities, tenantId);
  }

  /** Internal method for system-managed roles. Bypasses reserved name validation. */
  public Role updateRoleInternal(
      @NotBlank final String roleId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities,
      String tenantId) {
    Set<Capability> scopedCapabilities = Capability.filterForTenantRole(capabilities);
    Optional<Role> roleOpt = findByIdAndTenant(roleId, tenantId);
    if (roleOpt.isEmpty()) {
      throw new ElementNotFoundException("Role not found with id: " + roleId);
    }
    Role role = roleOpt.get();
    role.setName(roleName);
    role.setDescription(roleDescription);
    role.setCapabilities(scopedCapabilities);
    return roleRepository.save(role);
  }

  /** Reads the caller's rights in the role's tenant, not in the tenant of the route. */
  private void assertCanAssignInTenant(String tenantId, Set<Capability> capabilities) {
    permissionService.inTenant(
        tenantId,
        () -> {
          assertCanAssignCapabilities(
              userService.currentUser(), capabilities, CapabilityScope.TENANT);
          return null;
        });
  }

  // -- DELETE --

  public void delete(@NotBlank final String tenantId, @NotBlank final String roleId) {
    Role role = loadInTenants(roleId, List.of(tenantId));
    ReservedKeyValidator.validateRoleId(role.getId());
    groupRepository.findAllByRoles(role).forEach(group -> group.getRoles().remove(role));
    roleRepository.delete(role);
  }
}
