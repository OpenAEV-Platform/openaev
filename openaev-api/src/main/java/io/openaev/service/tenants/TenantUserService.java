package io.openaev.service.tenants;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.specification.UserSpecification.fromIds;
import static io.openaev.database.specification.UserSpecification.inTenant;
import static io.openaev.database.specification.UserSpecification.organizationsInTenant;
import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserMapper;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawUser;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.UserSpecification;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.TenantWriteScopeException;
import io.openaev.service.UserCreationScope;
import io.openaev.service.UserService;
import io.openaev.service.account.PrivilegeEscalationValidator;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.service.organization.OrganizationService;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.users.UserQueryHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TenantUserService implements DependenciesManager {

  private final UserService userService;
  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final TenantMembershipCacheManager tenantMembershipCacheManager;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final OrganizationService organizationService;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  /**
   * Creates a user and attaches it to the tenant of the request's write scope, or silently attaches
   * an existing user.
   */
  public UserOutput createOrAttach(TxCtx ctx, UserInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    BaseEvent.attributeRequestEventsTo(tenantId, User.class);
    var existingUser = userRepository.findByEmailIgnoreCase(input.email());
    if (existingUser.isPresent()) {
      String userId = existingUser.get().getId();
      attachToTenant(userId, tenantId);
      userService.assignAutoAssignGroups(userId, List.of(tenantId));
      // Reload user after @Modifying queries cleared the persistence context
      User reloaded = userRepository.findById(userId).orElseThrow();
      return toOutput(reloaded, tenantId);
    }
    User user = userService.createUser(input, UserCreationScope.TENANT);
    applyOrganization(user, input.organizationId(), tenantId);
    attachToTenant(user.getId(), tenantId);
    userService.assignAutoAssignGroups(user.getId(), List.of(tenantId));
    // Reload user after @Modifying queries cleared the persistence context
    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    return toOutput(reloaded, tenantId);
  }

  public void attachToTenant(@NotBlank String userId, @NotBlank String tenantId) {
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);
  }

  // -- READ --

  /** Returns a user by ID within the tenant of the request. */
  @Transactional(readOnly = true)
  public UserOutput user(TxCtx ctx, @NotBlank final String userId) {
    String tenantId = readTenant(ctx);
    return userRepository
        .findOne(inTenant(tenantId).and(UserSpecification.byId(userId)))
        .map(user -> toOutput(user, tenantId))
        .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
  }

  /** Finds users by IDs within the tenant of the request. */
  @Transactional(readOnly = true)
  public List<UserOutput> find(TxCtx ctx, @NotNull final List<String> userIds) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    String tenantId = readTenant(ctx);
    return toOutputs(userRepository.findAll(inTenant(tenantId).and(fromIds(userIds))), tenantId);
  }

  /** Returns all users belonging to the tenant of the request. */
  @Transactional(readOnly = true)
  public List<RawUser> users(TxCtx ctx) {
    return userRepository.rawAllInTenant(readTenant(ctx));
  }

  // -- SEARCH --

  /** Searches users belonging to the tenant of the request. */
  @Transactional(readOnly = true)
  public Page<UserOutput> search(TxCtx ctx, SearchPaginationInput searchPaginationInput) {
    String tenantId = readTenant(ctx);
    Specification<User> tenantSpec = inTenant(tenantId);
    // After the filters, which may join the organizations of the user (see organizationsInTenant).
    Specification<User> organizationsOfTenant = organizationsInTenant(tenantId);
    var cb = entityManager.getCriteriaBuilder();
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            paginate(
                entityManager,
                User.class,
                tenantSpec.and(spec).and(organizationsOfTenant),
                tenantSpec.and(specCount).and(organizationsOfTenant),
                pageable,
                (cq, root) -> UserQueryHelper.select(cb, cq, root, tenantId),
                UserQueryHelper::execution),
        searchPaginationInput,
        User.class);
  }

  // -- UPDATE --

  /**
   * Updates profile fields of a user within the tenant of the request's write scope. Does NOT
   * modify tenant memberships or admin status: those are platform-level operations.
   */
  public UserOutput update(TxCtx ctx, @NotBlank String userId, UserInput input) {
    ReservedKeyValidator.validateUserEmailPattern(input.email());
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    BaseEvent.attributeRequestEventsTo(tenantId, User.class);
    Specification<User> spec = inTenant(tenantId).and(UserSpecification.byId(userId));
    User existing =
        userRepository
            .findOne(spec)
            .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
    ReservedKeyValidator.validateUserEmailPattern(existing.getEmail());
    PrivilegeEscalationValidator.assertAdminFlagUnchanged(input.admin(), existing.isAdmin());
    userService.applyProfile(existing, input);
    applyOrganization(existing, input.organizationId(), tenantId);
    return toOutput(userRepository.save(existing), tenantId);
  }

  /**
   * Sets the organization the user holds in the given tenant, leaving the ones it holds in other
   * tenants untouched. An empty value clears that tenant's organization; an unknown organization,
   * or one of another tenant, is rejected rather than cleared.
   */
  private void applyOrganization(User user, String organizationId, String tenantId) {
    Organization organization = null;
    if (StringUtils.hasText(organizationId)) {
      organization = organizationService.findById(organizationId);
      if (!tenantId.equals(organization.getTenant().getId())) {
        throw new ElementNotFoundException("Organization not found: " + organizationId);
      }
    }
    userRepository.assignOrganization(user, tenantId, organization);
  }

  // -- OUTPUT --

  // Only the request's tenant's organization is exposed: a user may belong to another tenant's.
  private UserOutput toOutput(User user, String tenantId) {
    return toOutputs(List.of(user), tenantId).getFirst();
  }

  private List<UserOutput> toOutputs(List<User> users, String tenantId) {
    Map<String, Organization> organizations =
        organizationService.usersOrganizationsInTenant(users, tenantId);
    return users.stream().map(user -> UserMapper.toOutput(user, tenantId, organizations)).toList();
  }

  // -- DELETE --

  /** Detaches a user from the tenant of the request's write scope without deleting the user. */
  public void detach(TxCtx ctx, String userId) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    User user = userService.user(userId);
    ReservedKeyValidator.validateUserEmailPattern(user.getEmail());
    // Before the membership row goes away, so the groups it granted go with it.
    userService.revokeTenantGroups(userId, List.of(tenantId));
    tenantRepository.removeUserFromTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);
  }

  // -- DEPENDENCIES MANAGER --

  @Override
  public void createDependencyForTenant(Tenant tenant) {
    attachToTenant(currentUser().getId(), tenant.getId());
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // users_tenants rows are cascade-deleted via FK on tenants table
    for (String userId : userRepository.findUserIdsByTenantId(tenantId)) {
      tenantMembershipCacheManager.evict(userId, tenantId);
    }
  }

  // -- INTERNAL --

  /**
   * The tenant a read answers for: the one the request's scope pins. A request that resolves no
   * scope (a caller with no membership on the non-prefixed route) reads the ambient tenant, as it
   * did before the scope existed; a scope of several tenants has no single answer and is refused.
   */
  private String readTenant(TxCtx ctx) {
    Set<String> scoped = TxCtxScopeUtils.requestReadScope(ctx);
    if (scoped.size() != 1) {
      throw new TenantWriteScopeException(
          "A read of users needs a single-tenant scope; the request scope holds "
              + scoped.size()
              + " tenants. Provide an explicit tenant selector.");
    }
    return scoped.iterator().next();
  }
}
