package io.openaev.service.account;

import io.openaev.config.SessionManager;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TxCtx;
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

  /**
   * Key of the {@code @Lock} taken by the deployment and rotation entrypoints, which open the
   * transaction: the lock must wrap their commit so a deployment never stores a token being
   * revoked.
   */
  public static final String ACCOUNT_LOCK_KEY = "catalog-connector-account";

  private final SessionManager sessionManager;

  @Autowired
  public CatalogConnectorPrivilegeService(
      TenantRoleService tenantRoleService,
      TenantGroupService tenantGroupService,
      UserService userService,
      TenantUserService tenantUserService,
      MarkingClearanceCacheManager markingClearanceCacheManager,
      TenantWriteScopeResolver writeScopeResolver,
      SessionManager sessionManager) {
    super(
        tenantRoleService,
        tenantGroupService,
        userService,
        tenantUserService,
        markingClearanceCacheManager,
        writeScopeResolver);
    this.sessionManager = sessionManager;
  }

  @Override
  protected WellKnownAccess wellKnownAccess() {
    return ReservedServiceAccount.CATALOG_CONNECTOR.access();
  }

  @Override
  protected boolean receivesEveryMarking() {
    return true;
  }

  /** Email of the tenant's catalog connector account. */
  public static String accountEmail(String tenantId) {
    return ReservedServiceAccount.CATALOG_CONNECTOR.emailPattern().formatted(tenantId);
  }

  /**
   * Returns the tenant's catalog connector token, creating the account on the first call. The role
   * and group are resynced at every call. {@code ctx} is the scope the work runs under and must
   * contain {@code tenantId}, the tenant the role is written in.
   */
  @Transactional
  public String getOrCreateToken(TxCtx ctx, String tenantId) {
    String email = accountEmail(tenantId);
    Group group = ensureWellKnownGroupWithRole(ensureWellKnownRole(ctx, tenantId), tenantId);

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
    User user =
        userService
            .findByEmailIgnoreCase(accountEmail(tenantId))
            .orElseThrow(
                () -> new ElementNotFoundException("Catalog connector service account not found"));
    new ArrayList<>(user.getTokens()).forEach(userService::deleteUserToken);
    String token = userService.createUserToken(user, UUID.randomUUID().toString()).getValue();
    sessionManager.invalidateUserSession(user.getId());
    return token;
  }
}
