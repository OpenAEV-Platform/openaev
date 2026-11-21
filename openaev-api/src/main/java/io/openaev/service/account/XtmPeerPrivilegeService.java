package io.openaev.service.account;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import io.openaev.config.SessionManager;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.*;
import io.openaev.service.tenants.TenantUserService;
import java.util.*;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions the service accounts the sibling products of the XTM Platform read this platform with
 * (XTM peer links). One account per consumer, keyed by its email, in the default tenant and in the
 * {@link ReservedServiceAccount#XTM_PEER} group. Called by the deployment manager (SaaS Manager)
 * with the platform admin token only, through {@code XtmPeerTokenApi}.
 *
 * <p>An OpenAEV token carries no name: the {@code token_name} of the contract names the one token
 * of the peer account, so issuing revokes every token of the account and creates a new one, and
 * revoking deletes them all. The account itself is kept.
 */
@Service
@Slf4j
public class XtmPeerPrivilegeService extends AbstractPrivilegeService {

  /** {@code <consumer>@xtm.<product>.filigran.io}, the identity the deployment manager derives. */
  private static final Pattern PEER_EMAIL =
      Pattern.compile("^[a-z0-9-]{1,64}@xtm\\.[a-z0-9-]{1,32}\\.filigran\\.io$");

  private final SessionManager sessionManager;

  @Autowired
  public XtmPeerPrivilegeService(
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

  /** The issued token and the account it belongs to. */
  public record IssuedPeerToken(String userId, String token) {}

  @Override
  protected WellKnownAccess wellKnownAccess() {
    return ReservedServiceAccount.XTM_PEER.access();
  }

  @Override
  protected boolean receivesEveryMarking() {
    return true;
  }

  /** Refuses an email outside the peer identity space, so the API can never reach a person. */
  public static String normalizePeerEmail(String email) {
    String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    if (!PEER_EMAIL.matcher(normalized).matches()) {
      throw new BadRequestException(
          "The email of an XTM peer account must match <consumer>@xtm.<product>.filigran.io");
    }
    return normalized;
  }

  /**
   * Ensures the peer account (created when absent, its name and group converged when present) in
   * the default tenant, revokes its tokens and issues a new non-expiring one. {@code ctx} must
   * contain the default tenant.
   */
  @Transactional
  public IssuedPeerToken issueToken(TxCtx ctx, String email, String name) {
    String peerEmail = normalizePeerEmail(email);
    Group group =
        ensureWellKnownGroupWithRole(
            ensureWellKnownRole(ctx, DEFAULT_TENANT_UUID), DEFAULT_TENANT_UUID);
    String token = UUID.randomUUID().toString();

    Optional<User> existing = userService.findByEmailIgnoreCase(peerEmail);
    if (existing.isEmpty()) {
      User user = userService.createInternalUser(peerEmail, name, null, false, token);
      user.setGroups(new ArrayList<>(List.of(group)));
      tenantUserService.attachToTenant(user.getId(), DEFAULT_TENANT_UUID);
      userService.saveUser(user);
      log.info("XTM peer account {} created", peerEmail);
      return new IssuedPeerToken(user.getId(), token);
    }

    User user = existing.get();
    applyUserServiceAttributes(user, name, null, peerEmail, group);
    new ArrayList<>(user.getTokens()).forEach(userService::deleteUserToken);
    userService.createUserToken(user, token);
    tenantUserService.attachToTenant(user.getId(), DEFAULT_TENANT_UUID);
    userService.saveUser(user);
    sessionManager.invalidateUserSession(user.getId());
    log.info("XTM peer account {} token rotated", peerEmail);
    return new IssuedPeerToken(user.getId(), token);
  }

  /** Revokes every token of the peer account; keeps the account. False when there was none. */
  @Transactional
  public boolean revokeTokens(TxCtx ctx, String email) {
    String peerEmail = normalizePeerEmail(email);
    Optional<User> existing = userService.findByEmailIgnoreCase(peerEmail);
    if (existing.isEmpty()) {
      return false;
    }
    User user = existing.get();
    List<Token> tokens = new ArrayList<>(user.getTokens());
    tokens.forEach(userService::deleteUserToken);
    sessionManager.invalidateUserSession(user.getId());
    return !tokens.isEmpty();
  }
}
