package io.openaev.service;

import static io.openaev.database.specification.RoleSpecification.tenantScope;
import static io.openaev.service.account.PrivilegeEscalationValidator.assertCanAssignCapabilities;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityScope;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
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
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  public Role createRole(
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    String id = UUID.randomUUID().toString();
    assertCanAssignCapabilities(userService.currentUser(), capabilities, CapabilityScope.TENANT);
    ReservedKeyValidator.validateRoleId(id);
    return createRoleInternal(id, roleName, roleDescription, capabilities, null);
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
      String tenantId) {
    Capability.validateForTenantRole(capabilities);

    Role role = new Role();
    role.setId(id);
    role.setName(roleName);
    role.setDescription(roleDescription);
    role.setCapabilities(capabilities);
    if (tenantId != null) {
      role.setTenant(new Tenant(tenantId));
    } else {
      role.setTenant(entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
    }
    return roleRepository.save(role);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Optional<Role> findByIdAndTenant(
      @NotBlank final String roleId, @NotBlank final String tenantId) {
    return roleRepository.findByIdAndTenantId(roleId, tenantId);
  }

  @Transactional(readOnly = true)
  public Role findByIdInTenant(@NotBlank final String roleId) {
    String tenantId = TenantContext.getCurrentTenant();
    return roleRepository
        .findByIdAndTenantId(roleId, tenantId)
        .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));
  }

  @Transactional(readOnly = true)
  public List<Role> findAllByIdInTenant(@NotNull final Collection<String> roleIds) {
    String tenantId = TenantContext.getCurrentTenant();
    return roleRepository.findAllByIdInAndTenantId(roleIds, tenantId);
  }

  @Transactional(readOnly = true)
  public List<Role> findAll(@NotBlank final String tenantId) {
    return roleRepository.findAllByTenantId(tenantId);
  }

  @Transactional(readOnly = true)
  public Page<Role> search(
      SearchPaginationInput searchPaginationInput, @NotBlank final String tenantId) {
    return buildPaginationJPA(
        (Specification<Role> spec, org.springframework.data.domain.Pageable pageable) ->
            roleRepository.findAll(tenantScope(tenantId).and(spec), pageable),
        searchPaginationInput,
        Role.class);
  }

  // -- UPDATE --

  public Role updateRole(
      @NotBlank final String roleId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    Set<Capability> scopedCapabilities = Capability.filterForTenantRole(capabilities);
    assertCanAssignCapabilities(
        userService.currentUser(), scopedCapabilities, CapabilityScope.TENANT);
    ReservedKeyValidator.validateRoleId(roleId);
    return updateRoleInternal(
        roleId, roleName, roleDescription, scopedCapabilities, TenantContext.getCurrentTenant());
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

  // -- DELETE --

  public void delete(@NotBlank final String roleId) {
    Role role = findByIdInTenant(roleId);
    ReservedKeyValidator.validateRoleId(role.getId());
    groupRepository.findAllByRoles(role).forEach(group -> group.getRoles().remove(role));
    roleRepository.delete(role);
  }
}
