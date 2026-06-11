package io.openaev.service;

import static io.openaev.database.specification.RoleSpecification.tenantScope;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
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
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RoleService {

  private final RoleRepository roleRepository;
  private final GroupRepository groupRepository;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  public Role createRole(
      String tenantId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    return createRole(
        tenantId, UUID.randomUUID().toString(), roleName, roleDescription, capabilities);
  }

  public Role createRole(
      String tenantId,
      @NotBlank final String id,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    ReservedKeyValidator.validateRoleId(tenantId, id);
    return createRoleInternal(tenantId, id, roleName, roleDescription, capabilities);
  }

  /**
   * Internal method for system-managed roles (e.g. service accounts). Bypasses reserved name
   * validation.
   */
  public Role createRoleInternal(
      String tenantId,
      @NotBlank final String id,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    Capability.validateForTenantRole(capabilities);

    Role role = new Role();
    role.setId(id);
    role.setName(roleName);
    role.setDescription(roleDescription);
    role.setCapabilities(Capability.resolveWithParents(capabilities));
    role.setTenant(new Tenant(tenantId));
    return roleRepository.save(role);
  }

  // -- READ --

  public Optional<Role> findById(String id) {
    return roleRepository.findById(id);
  }

  public Role findByIdInTenant(TxCtx ctx, @NotBlank final String roleId) {
    return roleRepository
        .findByIdAndTenantId(roleId, ctx.tenantIdFromUri())
        .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));
  }

  public List<Role> findAll(@NotBlank final String tenantId) {
    return roleRepository.findAllByTenantId(tenantId);
  }

  public Page<Role> searchRole(
      SearchPaginationInput searchPaginationInput, @NotBlank final String tenantId) {
    return buildPaginationJPA(
        (Specification<Role> spec, org.springframework.data.domain.Pageable pageable) ->
            roleRepository.findAll(tenantScope(tenantId).and(spec), pageable),
        searchPaginationInput,
        Role.class);
  }

  // -- UPDATE --

  public Role updateRole(
      String tenantId,
      @NotBlank final String roleId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {

    ReservedKeyValidator.validateRoleId(tenantId, roleId);
    return updateRoleInternal(roleId, roleName, roleDescription, capabilities);
  }

  /** Internal method for system-managed roles. Bypasses reserved name validation. */
  public Role updateRoleInternal(
      @NotBlank final String roleId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    Capability.validateForTenantRole(capabilities);
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));
    role.setUpdatedAt(Instant.now());
    role.setName(roleName);
    role.setDescription(roleDescription);
    role.setCapabilities(Capability.resolveWithParents(capabilities));
    return roleRepository.save(role);
  }

  // -- DELETE --

  public void deleteRole(String tenantId, @NotBlank final String roleId) {
    Role role =
        roleRepository
            .findByIdAndTenantId(roleId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));
    ReservedKeyValidator.validateRoleId(tenantId, role.getId());
    List<Group> groups = groupRepository.findAllByRoles(role);
    for (Group g : groups) {
      g.getRoles().remove(role);
    }

    roleRepository.deleteById(roleId);
  }
}
