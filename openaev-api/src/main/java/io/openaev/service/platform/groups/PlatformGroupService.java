package io.openaev.service.platform.groups;

import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.api.platform.groups.PlatformGroupOutput;
import io.openaev.api.platform.groups.PlatformGroupQueryHelper;
import io.openaev.database.model.PlatformGroup;
import io.openaev.database.model.PlatformRole;
import io.openaev.database.model.User;
import io.openaev.database.repository.PlatformGroupRepository;
import io.openaev.database.repository.PlatformRoleRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.ReferenceResolver;
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

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PlatformGroupService {

  private final PlatformGroupRepository platformGroupRepository;
  private final PlatformRoleRepository platformRoleRepository;
  private final UserRepository userRepository;
  private final ReferenceResolver referenceResolver;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  public PlatformGroup createPlatformGroup(@NotBlank final String name, final String description) {
    PlatformGroup group = new PlatformGroup();
    group.setName(name);
    group.setDescription(description);
    return platformGroupRepository.save(group);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public PlatformGroup findById(@NotBlank final String id) {
    return platformGroupRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform group not found: " + id));
  }

  @Transactional(readOnly = true)
  public Page<PlatformGroupOutput> search(@NotNull SearchPaginationInput searchPaginationInput) {
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            paginate(
                entityManager,
                PlatformGroup.class,
                spec,
                specCount,
                pageable,
                PlatformGroupQueryHelper::select,
                PlatformGroupQueryHelper::execution),
        searchPaginationInput,
        PlatformGroup.class);
  }

  @Transactional(readOnly = true)
  public List<String> findUserIds(@NotBlank final String groupId) {
    return platformGroupRepository.findUserIdsByGroupId(groupId);
  }

  @Transactional(readOnly = true)
  public Set<String> findPlatformRoleIds(@NotBlank final String groupId) {
    return platformGroupRepository.findPlatformRoleIdsByGroupId(groupId);
  }

  // -- UPDATE --

  public PlatformGroup updatePlatformGroup(
      @NotBlank final String groupId, @NotBlank final String name, final String description) {
    PlatformGroup group = findById(groupId);
    group.setName(name);
    group.setDescription(description);
    return platformGroupRepository.save(group);
  }

  public List<String> updatePlatformGroupUsers(
      @NotBlank final String groupId, List<String> userIds) {
    PlatformGroup group = findById(groupId);
    group.setUsers(referenceResolver.resolve(userIds, User.class, userRepository::countByIdIn));
    platformGroupRepository.save(group);
    return platformGroupRepository.findUserIdsByGroupId(groupId);
  }

  public Set<String> updatePlatformGroupRoles(
      @NotBlank final String groupId, List<String> platformRoleIds) {
    PlatformGroup group = findById(groupId);
    group.setPlatformRoles(
        referenceResolver.resolve(
            platformRoleIds, PlatformRole.class, platformRoleRepository::countByIdIn));
    platformGroupRepository.save(group);
    return platformGroupRepository.findPlatformRoleIdsByGroupId(groupId);
  }

  // -- DELETE --

  public void deletePlatformGroup(@NotBlank final String groupId) {
    if (!platformGroupRepository.existsById(groupId)) {
      throw new EntityNotFoundException("Platform group not found: " + groupId);
    }
    platformGroupRepository.deleteByIdNative(groupId);
  }
}
