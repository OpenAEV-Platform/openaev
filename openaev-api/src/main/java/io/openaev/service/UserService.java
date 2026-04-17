package io.openaev.service;

import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;
import static io.openaev.utils.pagination.CriteriaBuilderPagination.paginate;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static java.time.Instant.now;

import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.config.DefaultOpenAEVPrincipal;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.config.SessionManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.GroupSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.ReferenceResolver;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.users.UserQueryHelper;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  /** Password encoder using Argon2 algorithm (Spring Security 5.8 defaults). */
  private final Argon2PasswordEncoder passwordEncoder =
      Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  @Resource private SessionManager sessionManager;
  @PersistenceContext private EntityManager entityManager;

  private final UserRepository userRepository;
  private final TagRepository tagRepository;
  private final GroupRepository groupRepository;
  private final TokenRepository tokenRepository;
  private final ReferenceResolver referenceResolver;
  private final CacheManager cacheManager;

  /** Cache for admin users to improve lookup performance. */
  private Cache adminCache;

  // -- COUNT --

  public long globalCount() {
    return userRepository.globalCount();
  }

  // -- CREATE --

  @Transactional(rollbackFor = Exception.class)
  public User createUser(UserInput input) {
    if (!StringUtils.hasLength(input.plainPassword())) {
      throw new IllegalArgumentException("Password is required when creating a user");
    }
    if (userRepository.findByEmailIgnoreCase(input.email()).isPresent()) {
      throw new DataIntegrityViolationException(
          "User with email " + input.email() + " already exists");
    }
    User user = new User();
    user.setUpdateAttributes(input);
    user.setTags(referenceResolver.resolve(input.tagIds(), Tag.class, tagRepository::countByIdIn));
    user.setOrganization(referenceResolver.resolve(input.organizationId(), Organization.class));
    return createUser(user, input.plainPassword(), UUID.randomUUID().toString());
  }

  /** Creates a user for internal/technical purposes (SSO login, connector provisioning). */
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
    return createUser(user, null, token);
  }

  private User createUser(User user, String password, String token) {
    if (StringUtils.hasLength(password)) {
      user.setPassword(this.encodeUserPassword(password));
    }
    List<Group> assignableGroups =
        groupRepository.findAll(GroupSpecification.defaultUserAssignable());
    user.setGroups(assignableGroups);
    User savedUser = userRepository.save(user);
    this.createUserToken(savedUser, token);
    return savedUser;
  }

  // -- READ --

  /** Returns a user by ID (platform scope, no tenant filtering). */
  @Transactional(readOnly = true)
  public User user(@NotBlank final String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
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
                UserQueryHelper::execution),
        searchPaginationInput,
        User.class);
  }

  // -- UPDATE --

  @Transactional(rollbackFor = Exception.class)
  public User updateUser(String userId, UserInput input) {
    User existing = user(userId);
    existing.setUpdateAttributes(input);
    existing.setTags(
        referenceResolver.resolve(input.tagIds(), Tag.class, tagRepository::countByIdIn));
    existing.setOrganization(referenceResolver.resolve(input.organizationId(), Organization.class));
    if (StringUtils.hasLength(input.plainPassword())) {
      existing.setPassword(this.encodeUserPassword(input.plainPassword()));
    }
    User savedUser = userRepository.save(existing);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
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

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  public void delete(String userId) {
    User existing = user(userId);
    if (existing == null) {
      throw new EntityNotFoundException("User not found: " + userId);
    }
    sessionManager.invalidateUserSession(userId);
    userRepository.deleteByIdNative(userId);
  }

  // -- AUTH --

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

  public Optional<User> findByToken(@NotBlank final String token) {
    return this.userRepository.findByToken(token);
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
}
