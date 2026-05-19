package io.openaev.service.tenants;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.specification.UserSpecification.fromIds;
import static io.openaev.database.specification.UserSpecification.inTenant;
import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserMapper;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawUser;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.GroupSpecification;
import io.openaev.database.specification.UserSpecification;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.users.UserQueryHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TenantUserService implements DependenciesManager {

  private final UserService userService;
  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final GroupRepository groupRepository;
  private final TenantMembershipCacheManager tenantMembershipCacheManager;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  /**
   * Creates a user and attaches it to the current tenant, or silently attaches an existing user.
   */
  public UserOutput createOrAttach(UserInput input) {
    String tenantId = tenantId();
    var existingUser = userRepository.findByEmailIgnoreCase(input.email());
    if (existingUser.isPresent()) {
      String userId = existingUser.get().getId();
      attachToTenant(userId, tenantId);
      assignDefaultTenantGroups(userId, tenantId);
      // Reload user after @Modifying queries cleared the persistence context
      User reloaded = userRepository.findById(userId).orElseThrow();
      return UserMapper.toOutput(reloaded);
    }
    User user = userService.createUser(input);
    attachToTenant(user.getId(), tenantId);
    assignDefaultTenantGroups(user.getId(), tenantId);
    // Reload user after @Modifying queries cleared the persistence context
    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    return UserMapper.toOutput(reloaded);
  }

  /** Attaches a user to the specified tenant. Does nothing if already attached. */
  public void attachToTenant(@NotBlank String userId, @NotBlank String tenantId) {
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);
  }

  // -- READ --

  /** Returns a user by ID within the current tenant scope. */
  @Transactional(readOnly = true)
  public UserOutput user(@NotBlank final String userId) {
    return userRepository
        .findOne(inTenant(tenantId()).and(UserSpecification.byId(userId)))
        .map(UserMapper::toOutput)
        .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
  }

  /** Finds users by IDs within the current tenant scope. */
  @Transactional(readOnly = true)
  public List<UserOutput> find(@NotNull final List<String> userIds) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    return userRepository.findAll(inTenant(tenantId()).and(fromIds(userIds))).stream()
        .map(UserMapper::toOutput)
        .toList();
  }

  /** Returns all users belonging to the current tenant. */
  @Transactional(readOnly = true)
  public List<RawUser> users() {
    return userRepository.rawAllInTenant(tenantId());
  }

  // -- SEARCH --

  /** Searches users belonging to the current tenant (from {@link TenantContext}). */
  @Transactional(readOnly = true)
  public Page<UserOutput> search(SearchPaginationInput searchPaginationInput) {
    Specification<User> tenantSpec = inTenant(tenantId());
    var cb = entityManager.getCriteriaBuilder();
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            paginate(
                entityManager,
                User.class,
                tenantSpec.and(spec),
                tenantSpec.and(specCount),
                pageable,
                (cq, root) -> UserQueryHelper.select(cb, cq, root),
                UserQueryHelper::execution),
        searchPaginationInput,
        User.class);
  }

  // -- UPDATE --

  /**
   * Updates profile fields of a user within the current tenant scope. Does NOT modify tenant
   * memberships or admin status — those are platform-level operations.
   */
  public UserOutput update(@NotBlank String userId, UserInput input) {
    Specification<User> spec = inTenant(tenantId()).and(UserSpecification.byId(userId));
    User existing =
        userRepository
            .findOne(spec)
            .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
    existing.setEmail(input.email());
    existing.setFirstname(input.firstname());
    existing.setLastname(input.lastname());
    existing.setPhone(input.phone());
    existing.setPhone2(input.phone2());
    existing.setPgpKey(input.pgpKey());
    existing.setOrganization(userService.resolveOrganization(input.organizationId()));
    existing.setTags(userService.resolveTags(input.tagIds()));
    User savedUser = userRepository.save(existing);
    return UserMapper.toOutput(savedUser);
  }

  // -- DELETE --

  /** Detaches a user from the current tenant without deleting the user. */
  public void detach(String userId) {
    tenantRepository.removeUserFromTenant(userId, tenantId());
    tenantMembershipCacheManager.evict(userId, tenantId());
  }

  // -- DEPENDENCIES MANAGER --

  @Override
  public void createDependencyForTenant(Tenant tenant) {
    attachToTenant(currentUser().getId(), tenant.getId());
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // users_tenants rows are cascade-deleted via FK on tenants table
  }

  // -- INTERNAL --

  /** Resolves the current tenant ID from the thread-local context. */
  private String tenantId() {
    String tenantId = TenantContext.getCurrentTenant();
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalStateException("TenantUserService requires a tenant context");
    }
    return tenantId;
  }

  private void assignDefaultTenantGroups(String userId, String tenantId) {
    List<Group> defaultGroups =
        groupRepository.findAll(GroupSpecification.defaultUserAssignableTenant(tenantId));
    if (defaultGroups.isEmpty()) {
      return;
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
    for (Group group : defaultGroups) {
      if (!group.getUsers().contains(user)) {
        group.getUsers().add(user);
        groupRepository.save(group);
      }
    }
  }
}
