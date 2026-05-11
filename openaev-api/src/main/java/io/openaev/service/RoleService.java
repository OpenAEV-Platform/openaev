package io.openaev.service;

import static io.openaev.database.specification.RoleSpecification.tenantScope;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(rollbackFor = Exception.class)
public class RoleService {

  private final RoleRepository roleRepository;
  private final GroupRepository groupRepository;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  public Role createRole(
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    return createRole(UUID.randomUUID().toString(), roleName, roleDescription, capabilities);
  }

  public Role createRole(
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
    role.setTenant(entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
    return roleRepository.save(role);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Optional<Role> findById(String id) {
    return roleRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public Role findByIdInTenant(@NotBlank final String roleId) {
    String tenantId = TenantContext.getCurrentTenant();
    return roleRepository
        .findByIdAndTenantId(roleId, tenantId)
        .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));
  }

  @Transactional(readOnly = true)
  public List<Role> findAll(@NotBlank final String tenantId) {
    return roleRepository.findAllByTenantId(tenantId);
  }

  @Transactional(readOnly = true)
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
      @NotBlank final String roleId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    Capability.validateForTenantRole(capabilities);
    String tenantId = TenantContext.getCurrentTenant();
    Role role =
        roleRepository
            .findByIdAndTenantId(roleId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));
    role.setUpdatedAt(Instant.now());
    role.setName(roleName);
    role.setDescription(roleDescription);
    role.setCapabilities(Capability.resolveWithParents(capabilities));
    return roleRepository.save(role);
  }

  // -- DELETE --

  public void deleteRole(@NotBlank final String roleId) {
    String tenantId = TenantContext.getCurrentTenant();
    Role role =
        roleRepository
            .findByIdAndTenantId(roleId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));

    List<Group> groups = groupRepository.findAllByRoles(role);
    for (Group g : groups) {
      g.getRoles().remove(role);
    }

    roleRepository.deleteById(roleId);
  }
}
