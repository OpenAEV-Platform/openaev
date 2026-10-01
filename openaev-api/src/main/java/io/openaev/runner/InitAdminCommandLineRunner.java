package io.openaev.runner;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.database.model.Token.ADMIN_TOKEN_UUID;
import static io.openaev.database.model.User.ADMIN_FIRSTNAME;
import static io.openaev.database.model.User.ADMIN_LASTNAME;
import static io.openaev.database.model.User.ADMIN_UUID;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.service.tenants.TenantUserService;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class InitAdminCommandLineRunner implements CommandLineRunner {

  @Value("${openbas.admin.email:${openaev.admin.email:#{null}}}")
  private String adminEmail;

  @Value("${openbas.admin.password:${openaev.admin.password:#{null}}}")
  private String adminPassword;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  private final UserRepository userRepository;
  private final TokenRepository tokenRepository;
  private final TenantUserService tenantUserService;
  private final AdminPrivilegeService adminPrivilegeService;
  private final TenantScopedTransaction tenantScopedTransaction;

  public InitAdminCommandLineRunner(
      @NotNull final UserRepository userRepository,
      @NotNull final TokenRepository tokenRepository,
      @NotNull final TenantUserService tenantUserService,
      @NotNull final AdminPrivilegeService adminPrivilegeService,
      @NotNull final TenantScopedTransaction tenantScopedTransaction) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.tenantUserService = tenantUserService;
    this.adminPrivilegeService = adminPrivilegeService;
    this.tenantScopedTransaction = tenantScopedTransaction;
  }

  @Override
  @Transactional
  public void run(String... args) {
    // This transaction reads a tenant-scoped table: the well-known groups ensured below carry an
    // eager Group.markings association, and marking_definitions is filtered on the transaction's
    // scope. Without one the read is fail-closed, so the groups come back granting no marking at
    // all - benign today only because clearance is read through JdbcTemplate rather than that
    // association. The scope is the default tenant rather than every tenant: a marking belongs to
    // exactly one tenant (marking_definitions.tenant_id is NOT NULL, there is no platform marking),
    // the default tenant is the only one this runner provisions, and a group may only grant
    // markings
    // of its own tenant. A wider scope could therefore only surface another tenant's clearance
    // grant
    // on a group that must not carry it.
    //
    // The scope is set on the transaction this method already owns, not in a new one: the whole
    // bootstrap must stay a single unit of work.
    this.tenantScopedTransaction.setScopeOnCurrentTransaction(TxCtx.forTenant(DEFAULT_TENANT_UUID));

    // Handle admin user
    Optional<User> adminUserOptional = this.userRepository.findById(ADMIN_UUID);
    User adminUser = adminUserOptional.map(this::updateUser).orElseGet(this::createUser);

    // Handle admin token
    Optional<Token> adminToken =
        this.tokenRepository.findFirstByUserIdOrderByCreatedAsc(adminUser.getId());
    adminToken.ifPresentOrElse(this::updateToken, () -> this.createToken(adminUser));

    // Ensure the admin user holds full administrative privileges through well-known groups: a
    // platform-scoped "Administrators" group (granting platform BYPASS) and the default-tenant
    // "Administrators" group (granting tenant BYPASS). The default tenant is created by a Flyway
    // migration and never runs the tenant-provisioning chain that seeds default groups for
    // API-created tenants, so both must be bootstrapped explicitly here, once the admin user is
    // guaranteed to exist. The platform group is ensured first because the tenant path clears the
    // persistence context last (see AdminPrivilegeService#ensureAdminGroup).
    this.adminPrivilegeService.ensurePlatformAdminGroup(adminUser);
    this.adminPrivilegeService.ensureAdminGroup(DEFAULT_TENANT_UUID, adminUser);
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
    tenantUserService.attachToTenant(ADMIN_UUID, DEFAULT_TENANT_UUID);
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
