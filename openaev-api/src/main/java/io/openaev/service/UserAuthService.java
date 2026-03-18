package io.openaev.service;

import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;
import static java.time.Instant.now;

import io.openaev.config.DefaultOpenAEVPrincipal;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Service for user authentication, session management, password encoding, and token handling.
 *
 * @see io.openaev.database.model.User
 * @see io.openaev.database.model.Token
 */
@Service
@RequiredArgsConstructor
public class UserAuthService {

  @Value("${openbas.admin.email:${openaev.admin.email:#{null}}}")
  private String adminEmail;

  private final Argon2PasswordEncoder passwordEncoder =
      Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  private final UserRepository userRepository;
  private final TokenRepository tokenRepository;
  private final CacheManager cacheManager;

  private Cache adminCache;

  // -- PASSWORD --

  /** Validates a user's password against their stored hash. */
  public boolean isUserPasswordValid(User user, String password) {
    return passwordEncoder.matches(password, user.getPassword());
  }

  /** Encodes a plaintext password using Argon2. */
  public String encodeUserPassword(String password) {
    return passwordEncoder.encode(password);
  }

  // -- SESSION --

  /** Creates a new security session for the user. */
  public void createUserSession(User user) {
    Authentication authentication = buildAuthenticationToken(user);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }

  /** Creates admin security session. */
  public void createAdminSession() {
    User adminUser = this.userRepository.findByEmailIgnoreCase(this.adminEmail).orElseThrow();
    this.createUserSession(adminUser);
  }

  /** Builds a Spring Security authentication token for a user. */
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

  // -- TOKEN --

  /** Creates a new API token for a user with a random value. */
  public void createUserToken(User user) {
    createUserToken(user, UUID.randomUUID().toString());
  }

  /** Creates a new API token for a user with a specific value. */
  public Token createUserToken(User user, String discreteToken) {
    Token token = new Token();
    token.setUser(user);
    token.setCreated(now());
    token.setValue(discreteToken);
    return tokenRepository.save(token);
  }

  // -- LOOKUP --

  /** Finds a user by their API token. */
  public Optional<User> findByToken(@NotBlank final String token) {
    return this.userRepository.findByToken(token);
  }

  /** Finds a user by email address (case-insensitive). */
  public Optional<User> findByEmailIgnoreCase(String email) {
    return userRepository.findByEmailIgnoreCase(email);
  }

  /** Retrieves the currently authenticated user (with admin cache). */
  public User currentUser() {
    if (adminCache == null) {
      adminCache = cacheManager.getCache("adminUsers");
    }
    if (adminCache != null) {
      User user = adminCache.get(SessionHelper.currentUser().getId(), User.class);
      if (user == null) {
        user =
            userRepository
                .findById(SessionHelper.currentUser().getId())
                .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
        if (user.isAdmin()) {
          adminCache.put(SessionHelper.currentUser().getId(), user);
        }
      }
      return user;
    }
    return userRepository
        .findById(SessionHelper.currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
  }
}
