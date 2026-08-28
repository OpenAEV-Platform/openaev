package io.openaev.api.platform.sessions;

import io.openaev.aop.AccessControl;
import io.openaev.config.SessionManager;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.session.SessionMapper;
import io.openaev.rest.session.response.SessionOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-wide session registry (Enterprise Edition). Unlike {@link
 * io.openaev.rest.session.SessionApi}, which scopes the listing to the current tenant's users,
 * these endpoints expose and manage every live session across all tenants of the platform.
 */
@RestController
@RequestMapping(PlatformSessionApi.PLATFORM_SESSIONS_URI)
@RequiredArgsConstructor
@Tag(
    name = "Platform sessions management",
    description = "Endpoints to manage platform-wide sessions")
public class PlatformSessionApi extends RestBehavior {

  public static final String PLATFORM_SESSIONS_URI = "/api/platform-sessions";

  private final SessionManager sessionManager;
  private final SessionMapper sessionMapper;

  @GetMapping
  @Transactional(readOnly = true)
  @AccessControl(
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_SESSION,
      isEnterpriseEdition = true)
  @Operation(
      summary = "List platform sessions",
      description = "List every live user session across the whole platform")
  public List<SessionOutput> sessions() {
    return sessionMapper.toSessionOutputs(sessionManager.findAllSessions());
  }

  @DeleteMapping("/{sessionId}")
  @Transactional
  @AccessControl(
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_SESSION,
      isEnterpriseEdition = true)
  @Operation(summary = "Kill a platform session", description = "Kill a single session by id")
  public ResponseEntity<Void> killSession(@PathVariable String sessionId) {
    return sessionManager.invalidateSession(sessionId)
        ? ResponseEntity.ok().build()
        : ResponseEntity.notFound().build();
  }

  @DeleteMapping("/user/{userId}")
  @Transactional
  @AccessControl(
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_SESSION,
      isEnterpriseEdition = true)
  @Operation(
      summary = "Kill platform user sessions",
      description = "Kill every live session of a user across the platform")
  public ResponseEntity<Void> killUserSessions(@PathVariable String userId) {
    sessionManager.invalidateUserSession(userId);
    return ResponseEntity.ok().build();
  }
}
