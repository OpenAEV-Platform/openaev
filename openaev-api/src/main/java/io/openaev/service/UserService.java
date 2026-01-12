package io.openaev.service;

import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static java.time.Instant.now;

import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.config.SessionManager;
import io.openaev.database.model.Group;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import io.openaev.database.specification.GroupSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.user.form.user.CreateUserInput;
import io.openaev.rest.user.form.user.UpdateUserInput;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for managing user accounts and authentication.
 *
 * <p>Provides methods for user CRUD operations, password management, token handling, and session
 * management. Admin users are cached for performance optimization.
 *
 * @see io.openaev.database.model.User
 * @see io.openaev.database.model.Token
 */
@Service
@RequiredArgsConstructor
public class UserService {
  @Resource private SessionManager sessionManager;

  /** Password encoder using Argon2 algorithm (Spring Security 5.8 defaults). */
  private final Argon2PasswordEncoder passwordEncoder =
      Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  private UserRepository userRepository;
  private TokenRepository tokenRepository;
  private TagRepository tagRepository;
  private GroupRepository groupRepository;
  private OrganizationRepository organizationRepository;
  private CacheManager cacheManager;

  /** Cache for admin users to improve lookup performance. */
  private Cache adminCache;

  @Autowired
  public void setOrganizationRepository(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  @Autowired
  public void setCacheManager(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  @Autowired
  public void setTagRepository(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  @Autowired
  public void setGroupRepository(GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
  }

  @Autowired
  public void setUserRepository(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Autowired
  public void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  /**
   * Returns the total count of users in the system.
   *
   * @return the number of users
   */
  public long globalCount() {
    return userRepository.globalCount();
  }

  // region users

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

  /**
   * Saves an updated user entity.
   *
   * @param user the user to update
   * @return the saved user
   */
  public User updateUser(User user) {
    return userRepository.save(user);
  }

  /**
   * Creates a new user from input data.
   *
   * <p>Handles password encoding, tag assignment, organization linking, and automatic group
   * assignment. Also creates an API token for the new user.
   *
   * @param input the user creation input
   * @param status the initial user status
   * @return the created user
   */
  public User createUser(CreateUserInput input, int status) {
    User user = new User();
    user.setUpdateAttributes(input);
    user.setStatus((short) status);
    if (StringUtils.hasLength(input.getPassword())) {
      user.setPassword(encodeUserPassword(input.getPassword()));
    }
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    // Find automatic groups to assign
    List<Group> assignableGroups =
        groupRepository.findAll(GroupSpecification.defaultUserAssignable());
    user.setGroups(assignableGroups);
    // Save the user
    User savedUser = userRepository.save(user);
    createUserToken(savedUser, input.getToken());
    return savedUser;
  }

  /**
   * Updates a user by ID with the provided input.
   *
   * @param userId the ID of the user to update
   * @param input the update data
   * @return the updated user
   * @throws ElementNotFoundException if the user is not found
   */
  public User updateUser(String userId, UpdateUserInput input) {
    User user = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
    return this.updateUser(user, input);
  }

  /**
   * Updates a user entity with the provided input.
   *
   * <p>Refreshes any active sessions for the user after update.
   *
   * @param user the user entity to update
   * @param input the update data
   * @return the updated user
   */
  public User updateUser(User user, UpdateUserInput input) {
    user.setUpdateAttributes(input);
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    User savedUser = userRepository.save(user);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
  }

  public Optional<User> findByToken(@NotBlank final String token) {
    return this.userRepository.findByToken(token);
  }

  /**
   * Retrieves a user by ID.
   *
   * @param userId the user ID
   * @return the user
   * @throws ElementNotFoundException if not found
   */
  public User user(@NotBlank final String userId) {
    return this.userRepository
        .findById(userId)
        .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
  }

  /**
   * Retrieves all users.
   *
   * @return list of all users
   */
  public List<User> users() {
    return this.userRepository.findAll();
  }

  /**
   * Retrieves the currently authenticated user.
   *
   * <p>Admin users are cached for performance. The cache is used if available, otherwise a database
   * lookup is performed.
   *
   * @return the current user
   * @throws ElementNotFoundException if the current user is not found
   */
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

  // endregion

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
    return new PreAuthenticatedAuthenticationToken(
        new OpenAEVPrincipal() {
          @Override
          public String getId() {
            return user.getId();
          }

          @Override
          public Collection<? extends GrantedAuthority> getAuthorities() {
            return roles;
          }

          @Override
          public boolean isAdmin() {
            return user.isAdmin();
          }

          @Override
          public String getLang() {
            return user.getLang();
          }
        },
        "",
        roles);
  }

  /**
   * Finds a user by email address (case-insensitive).
   *
   * @param email the email to search for
   * @return an Optional containing the user if found
   */
  public Optional<User> findByEmailIgnoreCase(String email) {
    return userRepository.findByEmailIgnoreCase(email);
  }
}
