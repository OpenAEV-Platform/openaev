package io.openaev.rest.user;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.TokenSpecification.active;
import static io.openaev.database.specification.TokenSpecification.fromUser;
import static io.openaev.helper.DatabaseHelper.updateRelation;

import io.openaev.aop.AccessControl;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.api.tenants.TenantMapper;
import io.openaev.api.tenants.TenantOutput;
import io.openaev.config.SessionManager;
import io.openaev.database.model.Action;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.user.form.me.UpdateMePasswordInput;
import io.openaev.rest.user.form.me.UpdateProfileInput;
import io.openaev.rest.user.form.user.RenewTokenInput;
import io.openaev.rest.user.form.user.UpdateUserInfoInput;
import io.openaev.service.UserService;
import io.openaev.service.tenants.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MeApi extends RestBehavior {

  public static final String ME_URI = "/api/me";
  private static final String TENANT_ME_URI = TENANT_PREFIX + "/me";

  private final SessionManager sessionManager;
  private final OrganizationRepository organizationRepository;
  private final TokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final Optional<AuditLogger> auditLogger;
  private final TenantService tenantService;

  @GetMapping("/api/logout")
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<Object> logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.setAttribute(SessionManager.EXPLICIT_LOGOUT, Boolean.TRUE);
    }
    return ResponseEntity.ok().build();
  }

  @GetMapping({ME_URI, TENANT_ME_URI})
  @Transactional
  @AccessControl(skipRBAC = true)
  public User me() {
    return userRepository
        .findById(currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
  }

  @PutMapping(ME_URI + "/profile")
  // Adding actionPerformed in the AccessControl annotation allows this endpoint to be audit logged.
  @Transactional
  @AccessControl(skipRBAC = true, actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  public User updateProfile(@Valid @RequestBody UpdateProfileInput input) {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    user.setUpdateAttributes(input);
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    User savedUser = userRepository.save(user);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
  }

  @PutMapping(ME_URI + "/information")
  // Adding actionPerformed in the AccessControl annotation allows this endpoint to be audit logged.
  @Transactional
  @AccessControl(skipRBAC = true, actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  public User updateInformation(@Valid @RequestBody UpdateUserInfoInput input) {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    user.setUpdateAttributes(input);
    User savedUser = userRepository.save(user);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
  }

  @PutMapping(ME_URI + "/password")
  // Adding actionPerformed in the AccessControl annotation allows this endpoint to be audit logged.
  @Transactional
  @AccessControl(skipRBAC = true, actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  public User updatePassword(
      @Valid @RequestBody UpdateMePasswordInput input, HttpServletRequest httpRequest)
      throws InputValidationException {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    if (userService.isUserPasswordValid(user, input.getCurrentPassword())) {
      user.setPassword(userService.encodeUserPassword(input.getPassword()));
      User savedUser = userRepository.save(user);
      // Security: a password change kills every other live session of the user; the session
      // that performed the change stays alive.
      sessionManager.invalidateOtherUserSessions(user.getId(), httpRequest.getSession().getId());
      return savedUser;
    } else {
      throw new InputValidationException("user_current_password", "Bad current password");
    }
  }

  @PostMapping(ME_URI + "/token/refresh")
  // Adding actionPerformed in the AccessControl annotation allows this endpoint to be audit logged.
  @AccessControl(skipRBAC = true, actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  @Transactional(rollbackFor = Exception.class)
  public Token renewToken(@Valid @RequestBody RenewTokenInput input) {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    Token token =
        tokenRepository
            .findByIdAndDeletedAtIsNull(input.getTokenId())
            .orElseThrow(ElementNotFoundException::new);
    if (!user.equals(token.getUser())) {
      throw new AccessDeniedException("You are not allowed to renew this token");
    }

    Instant renewedAt = Instant.now();
    token.setDeletedAt(renewedAt);
    token.setValue(renewedPlaceholderFor(token));
    tokenRepository.save(token);

    Token renewedToken = userService.createUserToken(user);
    auditLogger.ifPresent(
        logger -> {
          Map<String, Object> contextData = new LinkedHashMap<>();
          contextData.put("token_id", renewedToken.getId());
          contextData.put("previous_token_id", token.getId());
          contextData.put("masked_reference", token.getValue());

          logger.logEvent(
              AuditEvent.builder()
                  .eventType(EventType.MUTATION)
                  .eventScope(AuditEventScope.UPDATE)
                  .eventStatus(EventStatus.SUCCESS)
                  .resourceType(ResourceType.TOKEN)
                  .resourceId(renewedToken.getId())
                  .contextData(contextData)
                  .message("User token renewed")
                  .origin(AuditEventOrigin.REQUEST)
                  .build());
        });
    return renewedToken;
  }

  @GetMapping(ME_URI + "/tenants")
  @Transactional
  @AccessControl(skipRBAC = true)
  public List<TenantOutput> myTenants() {
    return tenantService.findTenantsByUserId(currentUser().getId()).stream()
        .map(TenantMapper::toOutput)
        .toList();
  }

  @GetMapping(ME_URI + "/tokens")
  @Transactional
  @AccessControl(skipRBAC = true)
  public List<Token> tokens() {
    return tokenRepository.findAll(fromUser(currentUser().getId()).and(active()));
  }

  private String renewedPlaceholderFor(Token token) {
    return "[RENEWED:%s]".formatted(token.getId());
  }
}
