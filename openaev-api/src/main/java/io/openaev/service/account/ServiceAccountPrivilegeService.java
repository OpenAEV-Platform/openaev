package io.openaev.service.account;

import static io.openaev.service.account.Constants.*;

import io.openaev.config.SessionManager;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.*;
import io.openaev.service.tenants.TenantUserService;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ServiceAccountPrivilegeService extends AbstractPrivilegeService {
  public static final String SERVICE_EMAIL_PATTERN = "service-%s@openaev.invalid";
  private static final String SERVICE_FIRSTNAME = "service";

  private final SessionManager sessionManager;

  @Autowired
  public ServiceAccountPrivilegeService(
      TenantRoleService tenantRoleService,
      TenantGroupService tenantGroupService,
      UserService userService,
      TenantUserService tenantUserService,
      SessionManager sessionManager) {
    super(tenantRoleService, tenantGroupService, userService, tenantUserService);
    this.sessionManager = sessionManager;
  }

  @Override
  protected String getRoleId() {
    return SERVICE_ROLE_ID;
  }

  @Override
  protected String getRoleName() {
    return SERVICE_ROLE_NAME;
  }

  @Override
  protected String getRoleDescription() {
    return SERVICE_ROLE_DESCRIPTION;
  }

  @Override
  protected Set<Capability> getRoleCapabilities() {
    return SERVICE_ROLE_CAPABILITIES;
  }

  @Override
  protected String getGroupId() {
    return SERVICE_GROUP_ID;
  }

  @Override
  protected String getGroupName() {
    return SERVICE_GROUP_NAME;
  }

  @Override
  protected String getGroupDescription() {
    return SERVICE_GROUP_DESCRIPTION;
  }

  @Transactional
  public void ensurePrivilegedUserExists(String tenantId) {
    Group group = createWellKnownGroupWithRole(createWellKnownRole(tenantId), tenantId);
    // UNIQUE by tenant
    String email = SERVICE_EMAIL_PATTERN.formatted(tenantId);

    Optional<User> existingEmailUser = userService.findByEmailIgnoreCase(email);

    if (existingEmailUser.isPresent()) {
      User user = existingEmailUser.get();
      // user.tokens collection is lazy, check in db.
      if (!userService.userHasToken(user.getId())) {
        // Email-matched user exists but has no token — reuse and attach token
        log.warn(
            "User with email {} already exists, but no token found. Reusing existing user.",
            user.getEmail());

        applyUserServiceAttributes(user, SERVICE_FIRSTNAME, null, email, group);

        userService.createUserToken(user);

        tenantUserService.attachToTenant(user.getId(), tenantId);
        userService.saveUser(user);
      }
    } else {
      // No user exists — create one
      User user =
          userService.createInternalUser(
              email, SERVICE_FIRSTNAME, null, false, UUID.randomUUID().toString());
      user.setGroups(new ArrayList<>(List.of(group)));
      tenantUserService.attachToTenant(user.getId(), tenantId);
      userService.saveUser(user);
    }
  }

  // Not @Transactional: every caller in this class is itself @Transactional, and a self-invocation
  // bypasses the Spring proxy — an annotation here would never take effect.
  public Optional<User> getUserServiceAccountByTenant(String tenantId) {
    String email = SERVICE_EMAIL_PATTERN.formatted(tenantId);

    return userService.findByEmailIgnoreCase(email).stream().findFirst();
  }

  @Transactional(readOnly = true)
  public String getTokenUserServiceAccountByTenant(String tenantId) {

    return getUserServiceAccountByTenant(tenantId)
        .map(User::getTokens)
        .filter(tokens -> !tokens.isEmpty())
        .map(tokens -> tokens.getFirst().getValue())
        .orElseThrow(() -> new UnsupportedOperationException("Token not found"));
  }

  /**
   * Rotates the tenant's service-account bearer token: deletes the existing one(s) and issues a
   * fresh value. The service account never logs in, so {@code UserService#renewUserToken} (which
   * only lets a token's own owner renew it) is unreachable for it; this is the only remediation
   * path if the token embedded in the agent installer command/endpoint leaks to a holder of {@code
   * INSTALL_AGENT} who isn't trusted to manage the account itself.
   *
   * <p>Also kills every live session of the service account. Token authentications are stateless
   * (see {@code AppSecurityConfig#hasTokenCredential}), so no new session can be minted from the
   * leaked token going forward - but a session established before this fix shipped, or before this
   * rotation runs, would otherwise keep authenticating as the service account without ever
   * presenting the token again, including to fetch the very token this call just issued.
   *
   * @param tenantId tenant whose service-account token must be rotated
   */
  @Transactional
  public void rotateTokenForTenant(String tenantId) {
    User user =
        getUserServiceAccountByTenant(tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Service account not found"));
    new ArrayList<>(user.getTokens()).forEach(userService::deleteUserToken);
    userService.createUserToken(user);
    sessionManager.invalidateUserSession(user.getId());
  }
}
