package io.openaev.rest.session;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.config.SessionManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.session.response.SessionOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

  @GetMapping({SESSION_URI, TENANT_SESSION_URI})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SESSION)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The list of live sessions")})
  @Operation(
      summary = "List tenant sessions",
      description = "List the live sessions of the current tenant's users")
  public List<SessionOutput> sessions() {
    // Sessions are platform-global (keyed only by user id); scope them to the
    // current tenant by intersecting with the tenant's user ids. The platform-wide
    // listing lives in PlatformSessionApi (/api/platform-sessions).
    return sessionManager.findSessionsForUsers(tenantUserIds()).stream()
        .map(SessionOutput::from)
        .toList();
  }

  private List<String> tenantUserIds() {
    return userRepository.findUserIdsByTenantId(TenantContext.getCurrentTenant());
  }

  @GetMapping({SESSION_URI + "/user/{userId}", TENANT_SESSION_URI + "/user/{userId}"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SESSION)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The list of live sessions of the user")
      })
  @Operation(summary = "List user sessions", description = "List the live sessions of a user")
  public List<SessionOutput> userSessions(@PathVariable String userId) {
    // Tenant scope: never expose sessions of users outside the current tenant.
    if (!tenantUserIds().contains(userId)) {
      return List.of();
    }
    return sessionManager.findUserSessions(userId).stream().map(SessionOutput::from).toList();
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
  public ResponseEntity<Void> killSession(@PathVariable String sessionId) {
    // Tenant scope: only sessions owned by the current tenant's users can be killed
    // from this endpoint (cross-tenant kill lives in PlatformSessionApi).
    boolean belongsToTenant =
        sessionManager.findSessionsForUsers(tenantUserIds()).stream()
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
  public ResponseEntity<Void> killUserSessions(@PathVariable String userId) {
    // Tenant scope: refuse to kill sessions of users outside the current tenant.
    if (!tenantUserIds().contains(userId)) {
      return ResponseEntity.notFound().build();
    }
    sessionManager.invalidateUserSession(userId);
    return ResponseEntity.ok().build();
  }
}
