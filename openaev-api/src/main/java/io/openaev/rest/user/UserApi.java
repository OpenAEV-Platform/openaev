package io.openaev.rest.user;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.SessionManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.rest.user.form.login.ResetUserInput;
import io.openaev.rest.user.form.user.ChangePasswordInput;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import io.openaev.utils.log.LogUtils;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@EnableAsync
@RequiredArgsConstructor
@UserRoleDescription
@Tag(
    name = "Users management",
    description = "Endpoints to manage users",
    externalDocs =
        @ExternalDocumentation(
            description = "Documentation about users",
            url = "https://docs.openaev.io/latest/administration/users/"))
public class UserApi extends RestBehavior {

  public static final String USER_URI = "/api/users";

  @Resource private SessionManager sessionManager;
  private final UserRepository userRepository;
  private final UserService userService;
  private final UserEventService userEventService;
  private final Optional<AuditLogger> auditLogger;

  @Operation(description = "Endpoint to login", summary = "Endpoint to login")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(schema = @Schema(implementation = User.class))),
      })
  @PostMapping("/api/login")
  @Transactional
  @AccessControl(skipRBAC = true)
  @UserRoleDescription(needAuthenticated = false)
  public User login(
      @Valid @RequestBody LoginUserInput input, HttpServletRequest httpRequest, TxCtx ctx) {
    Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(input.getLogin());
    if (optionalUser.isPresent()) {
      User user = optionalUser.get();
      if (userService.isUserPasswordValid(user, input.getPassword())) {
        userService.createUserSession(user);
        // Capture auth context in session for reliable expiry audit metadata.
        SessionManager.markAuthenticatedSession(httpRequest, user.getId());
        // Enforce the max concurrent sessions platform setting (oldest sessions are evicted).
        sessionManager.enforceSessionLimit(user.getId(), httpRequest.getSession().getId());
        userEventService.createLoginSuccessEvent(user);

        auditLogger.ifPresent(
            logger -> {
              logger.logAuthEvent(
                  AuditEventScope.LOGIN,
                  EventStatus.SUCCESS,
                  LogUtils.getAuthEventProviderLocal(),
                  null);
            });

        return user;
      }
    }
    userEventService.createLoginFailedEvent(
        "local login", BadCredentialsException.class.getSimpleName());

    auditLogger.ifPresent(
        logger -> {
          logger.logAuthEvent(
              AuditEventScope.LOGIN,
              EventStatus.ERROR,
              LogUtils.getAuthEventProviderLocal(),
              BadCredentialsException.class.getSimpleName());
        });

    throw new BadCredentialsException("Invalid credential.");
  }

  @Operation(description = "Reset the password", summary = "Password reset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Mail to reset the password sent"),
        @ApiResponse(responseCode = "400", description = "The user was not found")
      })
  @PostMapping("/api/reset")
  // Adding actionPerformed in the AccessControl annotation allows this endpoint to be audit logged.
  @Transactional
  @AccessControl(skipRBAC = true, actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  public ResponseEntity<?> passwordReset(@Valid @RequestBody ResetUserInput input, TxCtx ctx) {
    // async execution; check method annotation
    userService.requestPasswordReset(input);
    // force a 200 OK response even if no user was found
    // to avoid enumeration via status code
    return ResponseEntity.ok().build();
  }

  @Operation(description = "Change the password", summary = "Password change")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The password was changed",
            content = @Content(schema = @Schema(implementation = User.class))),
      })
  @PostMapping("/api/reset/{token}")
  // Adding actionPerformed in the AccessControl annotation allows this endpoint to be audit logged.
  @Transactional
  @AccessControl(skipRBAC = true, actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  public User changePasswordReset(
      @PathVariable @Schema(description = "Token generated during reset") String token,
      @Valid @RequestBody ChangePasswordInput input,
      TxCtx ctx)
      throws InputValidationException {
    return userService.resetPassword(token, input);
  }

  @Operation(
      description = "Validate that the reset token does exist",
      summary = "Check reset token")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Mail to reset the password sent",
            content = @Content(schema = @Schema(implementation = Boolean.class))),
      })
  @GetMapping("/api/reset/{token}")
  @AccessControl(skipRBAC = true)
  public boolean validatePasswordResetToken(
      @PathVariable @Schema(description = "Token generated during reset") String token) {
    return userService.getResetToken(token);
  }
}
