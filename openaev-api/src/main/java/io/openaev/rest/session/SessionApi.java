package io.openaev.rest.session;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.config.SessionManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.session.response.SessionOutput;
import io.openaev.utils.TxCtxScopeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Administration endpoints for the persistent session registry: list live sessions (platform-wide
 * or per user) and kill them. Sessions are stored in PostgreSQL, so these operations cover every
 * node and survive restarts.
 */
@RestController
@UserRoleDescription
@RequiredArgsConstructor
@Tag(name = "Sessions management", description = "Endpoints to manage user sessions")
public class SessionApi extends RestBehavior {

  public static final String SESSION_URI = "/api/sessions";
  // The frontend rewrites every call to /api/tenants/{tenantId}/..., so each
  // endpoint must expose both the plain and the tenant-prefixed path.
  public static final String TENANT_SESSION_URI = TENANT_PREFIX + "/sessions";

  private final SessionManager sessionManager;
  private final UserRepository userRepository;
  private final SessionMapper sessionMapper;

  @GetMapping({SESSION_URI, TENANT_SESSION_URI})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SESSION)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The list of live sessions")})
  @Operation(
      summary = "List tenant sessions",
      description = "List the live sessions of the users of the request's tenants")
  public List<SessionOutput> sessions(TxCtx ctx) {
    // Sessions are platform-global (keyed only by user id); scope them to the request's tenants by
    // intersecting with the user ids of those tenants. The platform-wide listing lives in
    // PlatformSessionApi (/api/platform-sessions).
    return sessionMapper.toSessionOutputs(sessionManager.findSessionsForUsers(tenantUserIds(ctx)));
  }

  /**
   * The users of the tenants of the request's scope. The access check has authorized the caller in
   * each of them.
   */
  private List<String> tenantUserIds(TxCtx ctx) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    return tenantIds.isEmpty() ? List.of() : userRepository.findUserIdsByTenantIdIn(tenantIds);
  }

  @GetMapping({SESSION_URI + "/user/{userId}", TENANT_SESSION_URI + "/user/{userId}"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SESSION)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The list of live sessions of the user")
      })
  @Operation(summary = "List user sessions", description = "List the live sessions of a user")
  public List<SessionOutput> userSessions(TxCtx ctx, @PathVariable String userId) {
    // Tenant scope: never expose sessions of users outside the request's tenants.
    if (!tenantUserIds(ctx).contains(userId)) {
      return List.of();
    }
    return sessionMapper.toSessionOutputs(sessionManager.findUserSessions(userId));
  }

  @DeleteMapping({SESSION_URI + "/{sessionId}", TENANT_SESSION_URI + "/{sessionId}"})
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SESSION)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The session was killed"),
        @ApiResponse(responseCode = "404", description = "The session was not found")
      })
  @Operation(summary = "Kill session", description = "Kill a single session by id")
  public ResponseEntity<Void> killSession(TxCtx ctx, @PathVariable String sessionId) {
    // Tenant scope: only sessions owned by users of the request's tenants can be killed from this
    // endpoint (cross-tenant kill lives in PlatformSessionApi).
    boolean belongsToTenant =
        sessionManager.findSessionsForUsers(tenantUserIds(ctx)).stream()
            .anyMatch(session -> session.sessionId().equals(sessionId));
    if (!belongsToTenant) {
      return ResponseEntity.notFound().build();
    }
    return sessionManager.invalidateSession(sessionId)
        ? ResponseEntity.ok().build()
        : ResponseEntity.notFound().build();
  }

  @DeleteMapping({SESSION_URI + "/user/{userId}", TENANT_SESSION_URI + "/user/{userId}"})
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SESSION)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The user sessions were killed")})
  @Operation(summary = "Kill user sessions", description = "Kill every live session of a user")
  public ResponseEntity<Void> killUserSessions(TxCtx ctx, @PathVariable String userId) {
    // Tenant scope: refuse to kill sessions of users outside the request's tenants.
    if (!tenantUserIds(ctx).contains(userId)) {
      return ResponseEntity.notFound().build();
    }
    sessionManager.invalidateUserSession(userId);
    return ResponseEntity.ok().build();
  }
}
