package io.openaev.service.platform.roles;

import static io.openaev.database.specification.RoleSpecification.platformScope;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

  // -- CREATE --

  public Role createPlatformRole(
      @NotBlank final String name,
      final String description,
      @NotNull final Set<Capability> capabilities) {
    Capability.validateForPlatformRole(capabilities);
    Role role = new Role();
    role.setName(name);
    role.setDescription(description);
    role.setCapabilities(Capability.resolveWithParents(capabilities));
    return roleRepository.save(role);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Role findById(String id) {
    return roleRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform role not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<Role> findByIds(List<String> ids) {
    return roleRepository.findAllById(ids);
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
    Capability.validateForPlatformRole(capabilities);
    Role role = findById(roleId);
    role.setName(name);
    role.setDescription(description);
    role.setCapabilities(Capability.resolveWithParents(capabilities));
    return roleRepository.save(role);
  }

  // -- DELETE --

  public void deletePlatformRole(@NotBlank final String roleId) {
    Role role = findById(roleId);
    roleRepository.deleteByIdNative(role.getId());
  }
}
