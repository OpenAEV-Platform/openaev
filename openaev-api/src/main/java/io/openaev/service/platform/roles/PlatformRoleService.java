package io.openaev.service.platform.roles;

import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.api.platform.roles.PlatformRoleOutput;
import io.openaev.api.platform.roles.PlatformRoleQueryHelper;
import io.openaev.database.model.Capability;
import io.openaev.database.model.PlatformRole;
import io.openaev.database.repository.PlatformRoleRepository;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(rollbackFor = Exception.class)
public class PlatformRoleService {

  private final PlatformRoleRepository platformRoleRepository;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  public PlatformRole createPlatformRole(
      @NotBlank final String name,
      final String description,
      @NotNull final Set<Capability> capabilities) {
    Capability.validateForPlatformRole(capabilities);
    PlatformRole role = new PlatformRole();
    role.setName(name);
    role.setDescription(description);
    role.setCapabilities(Capability.resolveWithParents(capabilities));
    return platformRoleRepository.save(role);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public PlatformRole findById(String id) {
    return platformRoleRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform role not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<PlatformRole> findByIds(List<String> ids) {
    return platformRoleRepository.findAllById(ids);
  }

  @Transactional(readOnly = true)
  public Page<PlatformRoleOutput> search(@NotNull SearchPaginationInput searchPaginationInput) {
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            paginate(
                entityManager,
                PlatformRole.class,
                spec,
                specCount,
                pageable,
                PlatformRoleQueryHelper::select,
                PlatformRoleQueryHelper::execution),
        searchPaginationInput,
        PlatformRole.class);
  }

  // -- UPDATE --

  public PlatformRole updatePlatformRole(
      @NotBlank final String roleId,
      @NotBlank final String name,
      final String description,
      @NotNull final Set<Capability> capabilities) {
    Capability.validateForPlatformRole(capabilities);
    PlatformRole role = findById(roleId);
    role.setName(name);
    role.setDescription(description);
    role.setCapabilities(Capability.resolveWithParents(capabilities));
    return platformRoleRepository.save(role);
  }

  // -- DELETE --

  public void deletePlatformRole(@NotBlank final String roleId) {
    if (!platformRoleRepository.existsById(roleId)) {
      throw new EntityNotFoundException("Platform role not found: " + roleId);
    }
    platformRoleRepository.deleteByIdNative(roleId);
  }
}
