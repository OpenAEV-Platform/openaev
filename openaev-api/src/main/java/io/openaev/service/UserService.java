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

@Service
@RequiredArgsConstructor
public class UserService {
  @Resource private SessionManager sessionManager;
  private final Argon2PasswordEncoder passwordEncoder =
      Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  private UserRepository userRepository;
  private TokenRepository tokenRepository;
  private TagRepository tagRepository;
  private GroupRepository groupRepository;
  private OrganizationRepository organizationRepository;
  private CacheManager cacheManager;
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

  public long globalCount() {
    return userRepository.globalCount();
  }

  // region users
  public boolean isUserPasswordValid(User user, String password) {
    return passwordEncoder.matches(password, user.getPassword());
  }

  public void createUserSession(User user) {
    Authentication authentication = buildAuthenticationToken(user);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }

  public String encodeUserPassword(String password) {
    return passwordEncoder.encode(password);
  }

  public void createUserToken(User user) {
    createUserToken(user, UUID.randomUUID().toString());
  }

  public Token createUserToken(User user, String discreteToken) {
    Token token = new Token();
    token.setUser(user);
    token.setCreated(now());
    token.setValue(discreteToken);
    return tokenRepository.save(token);
  }

  public User updateUser(User user) {
    return userRepository.save(user);
  }

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

  public User updateUser(String userId, UpdateUserInput input) {
    User user = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
    return this.updateUser(user, input);
  }

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

  public User user(@NotBlank final String userId) {
    return this.userRepository.findById(userId).orElseThrow();
  }

  public List<User> users() {
    return this.userRepository.findAll();
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

  // endregion

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

  public Optional<User> findByEmailIgnoreCase(String email) {
    return userRepository.findByEmailIgnoreCase(email);
  }
}
