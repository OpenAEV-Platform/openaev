package io.openaev.service.account;

import io.openaev.config.SessionManager;
import io.openaev.database.model.Group;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.*;
import io.openaev.service.tenants.TenantUserService;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides the per-tenant service account whose token is given to the connectors deployed from the
 * catalog.
 */
@Service
@Slf4j
public class CatalogConnectorPrivilegeService extends AbstractPrivilegeService {
  private static final String CATALOG_CONNECTOR_FIRSTNAME = "Catalog connectors";

  private final SessionManager sessionManager;

  @Autowired
  public CatalogConnectorPrivilegeService(
      TenantRoleService tenantRoleService,
      TenantGroupService tenantGroupService,
      UserService userService,
      TenantUserService tenantUserService,
      SessionManager sessionManager) {
    super(tenantRoleService, tenantGroupService, userService, tenantUserService);
    this.sessionManager = sessionManager;
  }

  @Override
  protected WellKnownAccess wellKnownAccess() {
    return ReservedServiceAccount.CATALOG_CONNECTOR.access();
  }

  /**
   * Returns the tenant's catalog connector token, creating the account on the first call. The role
   * and group are resynced at every call.
   */
  @Transactional
  public String getOrCreateToken(String tenantId) {
    String email = ReservedServiceAccount.CATALOG_CONNECTOR.emailPattern().formatted(tenantId);
    userService.lockAccountCreation(email);
    Group group = ensureWellKnownGroupWithRole(ensureWellKnownRole(tenantId), tenantId);

    Optional<User> existingUser = userService.findByEmailIgnoreCase(email);
    if (existingUser.isEmpty()) {
      String token = UUID.randomUUID().toString();
      User user =
          userService.createInternalUser(email, CATALOG_CONNECTOR_FIRSTNAME, null, false, token);
      user.setGroups(new ArrayList<>(List.of(group)));
      tenantUserService.attachToTenant(user.getId(), tenantId);
      userService.saveUser(user);
      return token;
    }

    User user = existingUser.get();
    Optional<String> existingToken = user.getTokens().stream().findFirst().map(Token::getValue);
    if (existingToken.isPresent()) {
      return existingToken.get();
    }
    log.warn(
        "User with email {} already exists, but no token found. Reusing existing user.",
        user.getEmail());
    applyUserServiceAttributes(user, CATALOG_CONNECTOR_FIRSTNAME, null, email, group);
    String token = userService.createUserToken(user, UUID.randomUUID().toString()).getValue();
    tenantUserService.attachToTenant(user.getId(), tenantId);
    userService.saveUser(user);
    return token;
  }

  /** Replaces the tenant's catalog connector token and returns the new one. */
  @Transactional
  public String rotateTokenForTenant(String tenantId) {
    String email = ReservedServiceAccount.CATALOG_CONNECTOR.emailPattern().formatted(tenantId);
    User user =
        userService
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () -> new ElementNotFoundException("Catalog connector service account not found"));
    new ArrayList<>(user.getTokens()).forEach(userService::deleteUserToken);
    String token = userService.createUserToken(user, UUID.randomUUID().toString()).getValue();
    sessionManager.invalidateUserSession(user.getId());
    return token;
  }
}
