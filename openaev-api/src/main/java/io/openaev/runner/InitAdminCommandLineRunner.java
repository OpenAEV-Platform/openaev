package io.openaev.runner;

import static io.openaev.database.model.Token.ADMIN_TOKEN_UUID;
import static io.openaev.database.model.User.*;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class InitAdminCommandLineRunner implements CommandLineRunner {

  @Value("${openaev.admin.email:#{null}}")
  private String adminEmail;

  @Value("${openaev.admin.password:#{null}}")
  private String adminPassword;

  @Value("${openaev.admin.token:#{null}}")
  private String adminToken;

  private final UserRepository userRepository;

  private final TokenRepository tokenRepository;

  public InitAdminCommandLineRunner(
      @NotNull final UserRepository userRepository,
      @NotNull final TokenRepository tokenRepository) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
  }

  @Override
  @Transactional
  public void run(String... args) {
    // Handle admin user
    Optional<User> adminUserOptional = this.userRepository.findById(ADMIN_UUID);
    User adminUser = adminUserOptional.map(this::updateUser).orElseGet(this::createUser);

    // Handle admin token
    Optional<Token> adminToken = this.tokenRepository.findById(ADMIN_TOKEN_UUID);
    adminToken.ifPresentOrElse(this::updateToken, () -> this.createToken(adminUser));
  }

  // -- USER --

  private String encodedPassword() {
    Argon2PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    return passwordEncoder.encode(this.adminPassword);
  }

  private User createUser() {
    if (!hasText(this.adminEmail)) {
      log.error("Config properties 'openaev.admin.email' cannot be null");
      System.exit(1);
    } else if (!EmailValidator.getInstance().isValid(this.adminEmail)) {
      log.error("Config properties 'openaev.admin.email' should be a valid email address");
      System.exit(1);
    }
    if (!hasText(this.adminPassword)) {
      log.error("Config properties 'openaev.admin.password' cannot be null");
      System.exit(1);
    }

    this.userRepository.createAdmin(
        ADMIN_UUID, ADMIN_FIRSTNAME, ADMIN_LASTNAME, this.adminEmail, encodedPassword());
    return this.userRepository.findById(ADMIN_UUID).orElseThrow();
  }

  private User updateUser(@NotNull final User user) {
    if (hasText(this.adminEmail)) {
      if (!EmailValidator.getInstance().isValid(this.adminEmail)) {
        throw new IllegalArgumentException(
            "Config property 'openaev.admin.email' must be a valid email address with a valid domain.");
      }
      user.setEmail(this.adminEmail);
    }
    if (hasText(this.adminPassword)) {
      user.setPassword(encodedPassword());
    }

    return this.userRepository.save(user);
  }

  // -- TOKEN --

  private void createToken(@NotNull final User user) {
    if (!hasText(this.adminToken)) {
      log.error("Config properties 'openaev.admin.token' cannot be null");
      System.exit(1);
    }
    try {
      UUID.fromString(this.adminToken);
    } catch (IllegalArgumentException e) {
      log.error("Config properties 'openaev.admin.token' should be a valid UUID");
      System.exit(1);
    }

    this.tokenRepository.createToken(
        ADMIN_TOKEN_UUID, user.getId(), this.adminToken, Instant.now());
  }

  private void updateToken(@NotNull final Token token) {
    if (hasText(this.adminToken)) {
      try {
        UUID.fromString(this.adminToken);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Config properties 'openaev.admin.token' should be a valid UUID");
      }
      token.setValue(this.adminToken);
    }

    this.tokenRepository.save(token);
  }
}
