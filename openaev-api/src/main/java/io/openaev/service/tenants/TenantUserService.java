package io.openaev.service.tenants;

import static io.openaev.database.specification.UserSpecification.fromIds;
import static io.openaev.database.specification.UserSpecification.inTenant;
import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserMapper;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawUser;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.UserSpecification;
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
public class TenantUserService {

  private final UserService userService;
  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  /**
   * Creates a user and attaches it to the current tenant, or silently attaches an existing user.
   */
  public UserOutput createOrAttach(UserInput input) {
    String tenantId = tenantId();
    var existingUser = userRepository.findByEmailIgnoreCase(input.email());
    if (existingUser.isPresent()) {
      UserOutput output = UserMapper.toOutput(existingUser.get());
      tenantRepository.addUserToTenant(existingUser.get().getId(), tenantId);
      return output;
    }
    User user = userService.createUser(input);
    UserOutput output = UserMapper.toOutput(user);
    tenantRepository.addUserToTenant(user.getId(), tenantId);
    return output;
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

  // -- DELETE --

  /** Detaches a user from the current tenant without deleting the user. */
  public void detach(String userId) {
    tenantRepository.removeUserFromTenant(userId, tenantId());
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
}
