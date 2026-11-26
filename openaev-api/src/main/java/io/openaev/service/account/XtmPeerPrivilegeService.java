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
 *
 * <p>Only the consumers in {@link #ACCEPTED_CONSUMERS} get an account, and only under their own
 * email ({@code <consumer>@xtm.<product>.filigran.io}): the read-only role of {@link
 * ReservedServiceAccount#XTM_PEER} is the set of reads that consumer makes. The group receives no
 * marking on its own: the tenant administrator grants it the markings allowed to leave OpenAEV.
 */
@Service
@Slf4j
public class XtmPeerPrivilegeService extends AbstractPrivilegeService {

  /** {@code <consumer>@xtm.<product>.filigran.io}, the identity the deployment manager derives. */
  private static final Pattern PEER_EMAIL =
      Pattern.compile("^[a-z0-9-]{1,64}@xtm\\.[a-z0-9-]{1,32}\\.filigran\\.io$");

  /**
   * The consumers whose reads the peer role carries. Another consumer is refused until its reads
   * are named in {@link ReservedServiceAccount#XTM_PEER}: OpenCTI has no reader of an OpenAEV
   * token, and XTM One calls OpenAEV with a per-user JWT.
   */
  public static final Set<String> ACCEPTED_CONSUMERS = Set.of("opencrq");

  private final SessionManager sessionManager;

  @Autowired
  public XtmPeerPrivilegeService(
      TenantRoleService tenantRoleService,
      TenantGroupService tenantGroupService,
      UserService userService,
      TenantUserService tenantUserService,
      MarkingClearanceCacheManager markingClearanceCacheManager,
      TenantWriteScopeResolver writeScopeResolver,
      UserGroupMembershipService userGroupMembershipService,
      SessionManager sessionManager) {
    super(
        tenantRoleService,
        tenantGroupService,
        userService,
        tenantUserService,
        markingClearanceCacheManager,
        writeScopeResolver,
        userGroupMembershipService);
    this.sessionManager = sessionManager;
  }

  /** The issued token and the account it belongs to. */
  public record IssuedPeerToken(String userId, String token) {}

  @Override
  protected WellKnownAccess wellKnownAccess() {
    return ReservedServiceAccount.XTM_PEER.access();
  }

  /**
   * False: what leaves OpenAEV through a peer is what the tenant administrator grants the peer
   * group, never every marking by default.
   */
  @Override
  protected boolean receivesEveryMarking() {
    return false;
  }

  /**
   * Refuses a consumer the peer role was not built for, and an email that names another consumer
   * than the one declared, so one consumer can never mint the token of another.
   */
  public static String requireAcceptedConsumer(String consumer, String peerEmail) {
    String normalized = consumer == null ? "" : consumer.trim().toLowerCase(Locale.ROOT);
    if (!ACCEPTED_CONSUMERS.contains(normalized)) {
      throw new BadRequestException(
          "The XTM peer consumer '" + normalized + "' is not accepted by this platform");
    }
    if (!peerEmail.startsWith(normalized + "@")) {
      throw new BadRequestException(
          "The email of an XTM peer account must start with its consumer: " + normalized + "@");
    }
    return normalized;
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
  public IssuedPeerToken issueToken(TxCtx ctx, String consumer, String email, String name) {
    String peerEmail = normalizePeerEmail(email);
    requireAcceptedConsumer(consumer, peerEmail);
    Group group =
        ensureWellKnownGroupWithRole(
            ensureWellKnownRole(ctx, DEFAULT_TENANT_UUID), DEFAULT_TENANT_UUID);
    // A group provisioned before the peer stopped receiving every marking keeps the markings it
    // holds (the administrator trims them) but receives no new one.
    tenantGroupService.stopReceivingNewMarkings(group);
    String token = UUID.randomUUID().toString();

    Optional<User> existing = userService.findByEmailIgnoreCase(peerEmail);
    if (existing.isEmpty()) {
      User user = userService.createInternalUser(peerEmail, name, null, false, token);
      joinGroupAsNewAccount(user, group);
      tenantUserService.attachToTenant(user.getId(), DEFAULT_TENANT_UUID);
      userService.saveUser(user);
      log.info("XTM peer account {} created", peerEmail);
      return new IssuedPeerToken(user.getId(), token);
    }

    User user = existing.get();
    applyUserServiceAttributes(user, name, null, peerEmail, group);
    deleteTokens(user);
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
    boolean hadTokens = deleteTokens(user);
    sessionManager.invalidateUserSession(user.getId());
    return hadTokens;
  }

  /**
   * Deletes every token of the account and drops them from its token list too, so a later save of
   * the account in the same persistence context never merges a deleted token back.
   */
  private boolean deleteTokens(User user) {
    List<Token> tokens = new ArrayList<>(user.getTokens());
    tokens.forEach(userService::deleteUserToken);
    user.getTokens().removeAll(tokens);
    return !tokens.isEmpty();
  }
}
