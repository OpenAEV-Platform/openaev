package io.openaev.service;

import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;
import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static java.time.Instant.now;

import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.config.DefaultOpenAEVPrincipal;
import io.openaev.config.OpenAEVAnonymous;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.config.SessionManager;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.GroupSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.rest.user.form.login.ResetUserInput;
import io.openaev.rest.user.form.user.ChangePasswordInput;
import io.openaev.service.account.PrivilegeEscalationValidator;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.utils.RandomUtils;
import io.openaev.utils.ReferenceResolver;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.users.UserQueryHelper;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Service for managing user accounts and authentication.
 *
 * <p>Provides methods for user CRUD operations, password management, token handling, and session
 * management. Admin users are cached for performance optimization.
 *
 * @see io.openaev.database.model.User
 */
@Service
@RequiredArgsConstructor
public class UserService {

  @Value("${openbas.admin.email:${openaev.admin.email:#{null}}}")
  private String adminEmail;

  private static final long tenMinutes = 1000L * 60L * 10L;
  private final Map<String, String> resetTokenMap = new PassiveExpiringMap<>(tenMinutes);

  /** Password encoder using Argon2 algorithm (Spring Security 5.8 defaults). */
  private final Argon2PasswordEncoder passwordEncoder =
      Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  @Resource private SessionManager sessionManager;
  @PersistenceContext private EntityManager entityManager;

  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final GroupRepository groupRepository;
  private final TokenRepository tokenRepository;
  private final TenantRepository tenantRepository;
  private final ReferenceResolver referenceResolver;
  private final CacheManager cacheManager;
  private MailingService mailingService;
  private final RandomUtils randomUtils;
  private final TenantMembershipCacheManager tenantMembershipCacheManager;
  private final TenantScopedTransaction tenantTx;

  /** Cache for admin users to improve lookup performance. */
  private Cache adminCache;

  // -- COUNT --

  @Autowired
  public void setMailingService(@Lazy MailingService mailingService) {
    this.mailingService = mailingService;
  }

  /**
   * Returns the total count of users in the system.
   *
   * @return the number of users
   */
  public long globalCount() {
    return userRepository.globalCount();
  }

  // -- CREATE --

  /**
   * Creates a user. The {@link UserCreationScope} decides which auto-assign groups are granted, and
   * whether the tenants carried by the input are honoured.
   */
  @Transactional(rollbackFor = Exception.class)
  public User createUser(UserInput input, UserCreationScope scope) {
    if (!StringUtils.hasLength(input.plainPassword())) {
      throw new IllegalArgumentException("Password is required when creating a user");
    }
    ReservedKeyValidator.validateUserEmailPattern(input.email());
    if (userRepository.findByEmailIgnoreCase(input.email()).isPresent()) {
      throw new DataIntegrityViolationException(
          "User with email " + input.email() + " already exists");
    }
    PrivilegeEscalationValidator.assertAdminFlagUnchanged(input.admin(), false);
    // A tenant creator has no authority over other tenants: it never attaches any, the caller
    // attaches its own right after.
    List<String> tenantIds = scope == UserCreationScope.PLATFORM ? input.tenantIds() : List.of();
    User user = new User();
    user.setUpdateAttributes(input);
    user.setTags(referenceResolver.resolve(input.tagIds(), Tag.class, tagRepository::countByIdIn));
    user.setOrganization(referenceResolver.resolve(input.organizationId(), Organization.class));
    user.setTenants(
        new ArrayList<>(
            referenceResolver.resolve(tenantIds, Tenant.class, tenantRepository::countByIdIn)));
    // The user's id is generated on save (UUID generator), not before: evict only after
    // persisting, using the saved user's id, or evictForUser is called with a null key.
    User createdUser = createUser(user, input.plainPassword(), UUID.randomUUID().toString(), scope);
    if (!CollectionUtils.isEmpty(tenantIds)) {
      tenantMembershipCacheManager.evictForUser(createdUser.getId(), tenantIds);
    }
    return createdUser;
  }

  /**
   * Creates a user for internal/technical purposes (SSO login, connector provisioning, service
   * accounts). Such a user always lands in a tenant, attached by the caller right after: the
   * platform auto-assign groups are therefore never granted. Callers own the group assignment —
   * either explicitly (technical accounts) or through {@link #assignAutoAssignGroups(String,
   * Collection)} once the tenant is attached (SSO).
   */
  @Transactional(rollbackFor = Exception.class)
  public User createInternalUser(
      String email, String firstname, String lastname, boolean isAdmin, String token) {
    if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
      throw new DataIntegrityViolationException("User with email " + email + " already exists");
    }
    User user = new User();
    user.setEmail(email);
    user.setFirstname(firstname);
    user.setLastname(lastname);
    user.setAdmin(isAdmin);
    return createUser(user, null, token, UserCreationScope.TENANT);
  }

  private User createUser(User user, String password, String token, UserCreationScope scope) {
    if (StringUtils.hasLength(password)) {
      user.setPassword(this.encodeUserPassword(password));
    }
    // Creation enters every scope at once: the platform when created from the platform screen,
    // plus each tenant attached in the input.
    assignAutoAssignGroups(
        user,
        user.getTenants().stream().map(Tenant::getId).toList(),
        scope == UserCreationScope.PLATFORM);
    User savedUser = userRepository.save(user);
    this.createUserToken(savedUser, token);
    return savedUser;
  }

  // -- READ --

  /**
   * Returns a user by ID (platform scope, no tenant filtering).
   *
   * <p>No {@code @Transactional} here: {@code userRepository.findById(...)} already runs under its
   * own Spring Data JPA transaction.
   */
  public User user(@NotBlank final String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
  }

  /** Finds users by IDs (platform scope, no tenant filtering). */
  @Transactional(readOnly = true)
  public List<User> find(@NotNull final List<String> userIds) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    return userRepository.findAllById(userIds);
  }

  // -- SEARCH --

  /** Searches users with pagination (platform scope, no tenant filtering). */
  @Transactional(readOnly = true)
  public Page<UserOutput> search(SearchPaginationInput searchPaginationInput) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            paginate(
                entityManager,
                User.class,
                spec,
                specCount,
                pageable,
                (cq, root) -> UserQueryHelper.select(cb, cq, root),
                UserQueryHelper::executionWithTenants),
        searchPaginationInput,
        User.class);
  }

  // -- UPDATE --

  @Transactional(rollbackFor = Exception.class)
  public User updateUser(String userId, UserInput input) {
    // Check if new email is reserved
    ReservedKeyValidator.validateUserEmailPattern(input.email());
    User existing = user(userId);
    // Check if previous email is reserved
    ReservedKeyValidator.validateUserEmailPattern(existing.getEmail());
    // Capture old tenant IDs before update for cache eviction
    List<String> oldTenantIds =
        existing.getTenants() != null
            ? existing.getTenants().stream().map(Tenant::getId).toList()
            : List.of();
    PrivilegeEscalationValidator.assertAdminFlagUnchanged(input.admin(), existing.isAdmin());
    applyProfile(existing, input);
    existing.setTenants(
        new ArrayList<>(
            referenceResolver.resolve(
                input.tenantIds(), Tenant.class, tenantRepository::countByIdIn)));
    // Only tenants the user just joined trigger auto-assignment: re-applying it to tenants he
    // already belonged to would restore groups deliberately removed from within those tenants.
    List<String> currentTenantIds = existing.getTenants().stream().map(Tenant::getId).toList();
    List<String> attachedTenantIds =
        currentTenantIds.stream().filter(tenantId -> !oldTenantIds.contains(tenantId)).toList();
    assignAutoAssignGroups(existing, attachedTenantIds, false);
    // Symmetrically, a membership must not outlive the tenant attachment that granted it.
    List<String> detachedTenantIds =
        oldTenantIds.stream().filter(tenantId -> !currentTenantIds.contains(tenantId)).toList();
    revokeTenantGroups(existing, detachedTenantIds);
    User savedUser = userRepository.save(existing);
    // Evict cache for old tenants (removed memberships) and new tenants (added memberships)
    List<String> newTenantIds = input.tenantIds() != null ? input.tenantIds() : List.of();
    List<String> allAffectedTenants = new ArrayList<>(oldTenantIds);
    allAffectedTenants.addAll(newTenantIds);
    tenantMembershipCacheManager.evictForUser(userId, allAffectedTenants);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
  }

  public void applyProfile(User user, UserInput input) {
    user.setFirstname(input.firstname());
    user.setLastname(input.lastname());
    user.setPhone(input.phone());
    user.setPhone2(input.phone2());
    user.setPgpKey(input.pgpKey());
    user.setTags(referenceResolver.resolve(input.tagIds(), Tag.class, tagRepository::countByIdIn));
    user.setOrganization(referenceResolver.resolve(input.organizationId(), Organization.class));
  }

  /**
   * Saves a user entity directly. For internal/technical use only (SSO provisioning, connector
   * management, group mapping).
   */
  @Transactional(rollbackFor = Exception.class)
  public User saveUser(User user) {
    User savedUser = userRepository.save(user);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
  }

  /** Resolves an organization reference from its ID (or null if blank). */
  public Organization resolveOrganization(String organizationId) {
    return referenceResolver.resolve(organizationId, Organization.class);
  }

  /** Resolves tag references from their IDs. */
  public java.util.Set<Tag> resolveTags(List<String> tagIds) {
    return referenceResolver.resolve(tagIds, Tag.class, tagRepository::countByIdIn);
  }

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  public void delete(String userId) {
    User existing = user(userId);
    ReservedKeyValidator.validateUserEmailPattern(existing.getEmail());
    sessionManager.invalidateUserSession(userId);
    userRepository.deleteByIdNative(userId);
  }

  // -- AUTH --

  /**
   * Creates a reset token for the specified user; also sends an email with the created token
   *
   * @param input input object for the specific user account to reset
   */
  @Async
  public void requestPasswordReset(ResetUserInput input) {
    Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(input.getLogin());
    // always compute a random value to reduce gap in time
    // spent between user found and user not found branches
    // note: we still spend more time in the "user found" branch
    // due to sending an email
    String resetToken = randomUtils.getRandomAlphanumeric(64);
    if (optionalUser.isPresent()) {
      User user = optionalUser.get();
      String username = user.getName() != null ? user.getName() : user.getEmail();
      // Background @Async path (no ambient transaction, no v2 scope): sendEmail resolves the
      // tenant-scoped injectors table for the email notifier, and this reset flow is anonymous/
      // global (login only, no tenant selector), so it always uses the platform default tenant's
      // email integration, matching MailingService's own 3-arg overload default.
      if ("fr".equals(input.getLang())) {
        String subject = "Code de récupération OpenAEV: " + resetToken;
        String body =
            "Bonjour "
                + username
                + ",</br>"
                + "Nous avons reçu une demande de réinitialisation de votre mot de passe OpenAEV.</br>"
                + "Entrez le code de réinitialisation du mot de passe suivant : "
                + resetToken;
        tenantTx.execute(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
            () -> mailingService.sendEmail(subject, body, List.of(user)));
      } else {
        String subject = "OpenAEV account recovery code: " + resetToken;
        String body =
            "Hi "
                + username
                + ",</br>"
                + "A request has been made to reset your OpenAEV password.</br>"
                + "Enter the following password recovery code: "
                + resetToken;
        tenantTx.execute(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
            () -> mailingService.sendEmail(subject, body, List.of(user)));
      }
      // Store in memory reset token
      synchronized (resetTokenMap) {
        resetTokenMap.put(user.getId(), resetToken);
      }
    }
  }

  /**
   * Applies a change of password for the specified account and reset token
   *
   * @param token the reset token; must be valid and associated with the user account
   * @param input change password input object
   * @return a User object for the affected user account
   * @throws InputValidationException if the token does not exist or is not associated with the user
   *     account
   */
  public User resetPassword(String token, ChangePasswordInput input)
      throws InputValidationException {
    String userId = null;
    synchronized (resetTokenMap) {
      for (Map.Entry<String, String> entry : resetTokenMap.entrySet()) {
        if (entry.getValue().equals(token)) {
          userId = entry.getKey(); // don't break out
        }
      }
    }

    if (userId == null) {
      throw new AccessDeniedException("Invalid credentials");
    }

    String password = input.getPassword();
    String passwordValidation = input.getPasswordValidation();
    if (!passwordValidation.equals(password)) {
      throw new InputValidationException("password_validation", "Bad password validation");
    }
    User changeUser = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
    changeUser.setPassword(encodeUserPassword(password));
    User savedUser = userRepository.save(changeUser);
    synchronized (resetTokenMap) {
      resetTokenMap.remove(userId);
    }
    // Security: a password reset kills every live session of the user
    sessionManager.invalidateUserSession(userId);
    return savedUser;
  }

  /**
   * checks a reset token exists
   *
   * @param token the reset token
   * @return true if it exists
   */
  public boolean getResetToken(String token) {
    return resetTokenMap.get(token) != null;
  }

  /**
   * Validates a user's password against their stored hash.
   *
   * @param user the user to validate
   * @param password the plaintext password to check
   * @return true if the password matches
   */
  public boolean isUserPasswordValid(User user, String password) {
    return passwordEncoder.matches(password, user.getPassword());
  }

  /**
   * Creates a new security session for the user.
   *
   * <p>Sets up the Spring Security context with the user's authentication details.
   *
   * @param user the user to create a session for
   */
  public void createUserSession(User user) {
    Authentication authentication = buildAuthenticationToken(user);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }

  /** Creates admin security session */
  public void createAdminSession() {
    User adminUser = this.userRepository.findByEmailIgnoreCase(this.adminEmail).orElseThrow();
    this.createUserSession(adminUser);
  }

  /**
   * Encodes a plaintext password using Argon2.
   *
   * @param password the plaintext password
   * @return the encoded password hash
   */
  public String encodeUserPassword(String password) {
    return passwordEncoder.encode(password);
  }

  /** Returns true if the given user has at least one API token. */
  @Transactional(readOnly = true)
  public boolean userHasToken(String userId) {
    return tokenRepository.existsByUserId(userId);
  }

  /**
   * Creates a new API token for a user with a random value.
   *
   * @param user the user to create a token for
   */
  public void createUserToken(User user) {
    createUserToken(user, UUID.randomUUID().toString());
  }

  /**
   * Creates a new API token for a user with a specific value.
   *
   * @param user the user to create a token for
   * @param discreteToken the specific token value to use
   * @return the created token
   */
  public Token createUserToken(User user, String discreteToken) {
    Token token = new Token();
    token.setUser(user);
    token.setCreated(now());
    token.setValue(discreteToken);
    return tokenRepository.save(token);
  }

  public Optional<User> findByTokenAndTenantId(
      @NotBlank final String token, @NotBlank final String tenantId) {
    return this.userRepository.findByTokenAndTenantId(token, tenantId);
  }

  public Optional<User> findByToken(@NotBlank final String token) {
    return this.userRepository.findByToken(token);
  }

  /**
   * Returns the currently authenticated user, or {@code null} when the execution context has no
   * authenticated user (startup datapacks, scenario imports, scheduled jobs). Use this instead of
   * {@link #currentUser()} in code paths that may run outside an authenticated request.
   */
  public User currentUserOrNull() {
    OpenAEVPrincipal principal = SessionHelper.currentUser();
    if (principal instanceof OpenAEVAnonymous) {
      return null;
    }
    return this.userRepository.findById(principal.getId()).orElse(null);
  }

  public User currentUser() {
    User user;
    // If we don't have the cache, we get it
    if (adminCache == null) {
      adminCache = cacheManager.getCache("adminUsers");
    }
    // If the cache is available
    if (adminCache != null) {
      // We try to check if the user is in the cache
      user = adminCache.get(SessionHelper.currentUser().getId(), User.class);
      // If not, we get it
      if (user == null) {
        user =
            this.userRepository
                .findById(SessionHelper.currentUser().getId())
                .orElseThrow(() -> new ElementNotFoundException("Current user not found"));

        // If the user is admin, we put him in cache
        if (user.isAdmin()) {
          adminCache.put(SessionHelper.currentUser().getId(), user);
        }
      }
    } else {
      // If for some reason, the cache is unavailable, we just get the user and return it
      user =
          this.userRepository
              .findById(SessionHelper.currentUser().getId())
              .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    }
    return user;
  }

  /**
   * Builds a Spring Security authentication token for a user.
   *
   * <p>Creates a pre-authenticated token with the user's roles (ROLE_USER, and ROLE_ADMIN if
   * applicable) and principal information.
   *
   * @param user the user to build a token for
   * @return the authentication token
   */
  public static PreAuthenticatedAuthenticationToken buildAuthenticationToken(
      @NotNull final User user) {
    List<SimpleGrantedAuthority> roles = new ArrayList<>();
    roles.add(new SimpleGrantedAuthority(ROLE_USER));
    if (user.isAdmin()) {
      roles.add(new SimpleGrantedAuthority(ROLE_ADMIN));
    }

    OpenAEVPrincipal principal =
        new DefaultOpenAEVPrincipal(user.getId(), roles, user.isAdmin(), user.getLang());

    return new PreAuthenticatedAuthenticationToken(principal, "", roles);
  }

  public Optional<User> findByEmailIgnoreCase(String email) {
    return userRepository.findByEmailIgnoreCase(email);
  }

  /**
   * Grants the auto-assign groups of the given tenants to an already persisted user. Used when a
   * user joins a tenant outside of the create/update flows, i.e. when attached from a tenant
   * screen.
   */
  @Transactional(rollbackFor = Exception.class)
  public void assignAutoAssignGroups(
      @NotBlank final String userId, @NotNull final Collection<String> tenantIds) {
    if (tenantIds.isEmpty()) {
      return;
    }
    User user = user(userId);
    assignAutoAssignGroups(user, tenantIds, false);
    userRepository.save(user);
  }

  /**
   * Grants the default-assign groups of the scopes the user just entered: the platform scope at
   * creation, plus every tenant freshly attached. Scopes the user already belonged to are skipped,
   * so a group deliberately removed from a user is never re-granted by a later update. Never
   * removes an existing group membership.
   */
  private void assignAutoAssignGroups(
      User user, Collection<String> tenantIds, boolean includePlatformScope) {
    if (!includePlatformScope && tenantIds.isEmpty()) {
      return;
    }
    Specification<Group> spec =
        includePlatformScope ? GroupSpecification.defaultUserAssignablePlatform() : null;
    for (String tenantId : tenantIds) {
      Specification<Group> tenantSpec = GroupSpecification.defaultUserAssignableTenant(tenantId);
      spec = spec == null ? tenantSpec : spec.or(tenantSpec);
    }
    List<Group> applicableGroups = groupRepository.findAll(spec);
    if (applicableGroups.isEmpty()) {
      return;
    }
    List<Group> current = new ArrayList<>(user.getUnscopedGroups());
    for (Group group : applicableGroups) {
      if (!current.contains(group)) {
        current.add(group);
      }
    }
    user.setGroups(current);
  }

  /**
   * Revokes the groups of the given tenants from an already persisted user. Used when a user leaves
   * a tenant outside of the update flow, i.e. when detached from a tenant screen.
   */
  @Transactional(rollbackFor = Exception.class)
  public void revokeTenantGroups(
      @NotBlank final String userId, @NotNull final Collection<String> tenantIds) {
    if (tenantIds.isEmpty()) {
      return;
    }
    User user = user(userId);
    revokeTenantGroups(user, tenantIds);
    User savedUser = userRepository.save(user);
    sessionManager.refreshUserSessions(savedUser);
  }

  /**
   * Drops every group scoped to a tenant the user just left: a group grants capabilities inside its
   * own tenant only, so keeping it would leave access to a tenant the user no longer belongs to.
   * Platform groups and the groups of the remaining tenants are untouched.
   */
  private void revokeTenantGroups(User user, Collection<String> tenantIds) {
    if (tenantIds.isEmpty()) {
      return;
    }
    user.getUnscopedGroups()
        .removeIf(
            group -> group.getTenant() != null && tenantIds.contains(group.getTenant().getId()));
  }
}
