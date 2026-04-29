package io.openaev.rest.user;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.config.SessionManager;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.rest.user.form.login.ResetUserInput;
import io.openaev.rest.user.form.user.ChangePasswordInput;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
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
  private final TenantMembershipCacheManager tenantMembershipCacheManager;

  @Operation(description = "Endpoint to login", summary = "Endpoint to login")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(schema = @Schema(implementation = User.class))),
      })
  @PostMapping("/api/login")
  @AccessControl(skipRBAC = true)
  @UserRoleDescription(needAuthenticated = false)
  public User login(@Valid @RequestBody LoginUserInput input) {
    Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(input.getLogin());
    if (optionalUser.isPresent()) {
      User user = optionalUser.get();
      if (userService.isUserPasswordValid(user, input.getPassword())) {
        // Verify tenant membership if a tenant ID was provided
        if (input.getTenantId() != null && !input.getTenantId().isBlank()) {
          if (!tenantMembershipCacheManager.existsByUserIdAndTenantId(
              user.getId(), input.getTenantId())) {
            userEventService.createLoginFailedEvent(
                "local login", BadCredentialsException.class.getSimpleName());
            throw new BadCredentialsException("User does not belong to the requested tenant.");
          }
        }
        userService.createUserSession(user);
        userEventService.createLoginSuccessEvent(user);
        return user;
      }
    }
    userEventService.createLoginFailedEvent(
        "local login", BadCredentialsException.class.getSimpleName());
    throw new BadCredentialsException("Invalid credential.");
  }

  @Operation(description = "Reset the password", summary = "Password reset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Mail to reset the password sent"),
        @ApiResponse(responseCode = "400", description = "The user was not found")
      })
  @PostMapping("/api/reset")
  @AccessControl(skipRBAC = true)
  public ResponseEntity<?> passwordReset(@Valid @RequestBody ResetUserInput input) {
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
  @AccessControl(skipRBAC = true)
  public User changePasswordReset(
      @PathVariable @Schema(description = "Token generated during reset") String token,
      @Valid @RequestBody ChangePasswordInput input)
      throws InputValidationException {
    return userService.resetPassword(token, input);
  }

  @Operation(
      description = "Validate that the reset token does exist",
      summary = "Check reset token")
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

  @PutMapping(USER_URI + "/{userId}/password")
  @AccessControl(
      resourceId = "#userId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER)
  @Transactional(rollbackFor = Exception.class)
  @Operation(description = "Change the password of a user", summary = "Change password")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modified user")})
  public User changePassword(
      @PathVariable @Schema(description = "ID of the user") String userId,
      @Valid @RequestBody ChangePasswordInput input) {
    User user = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
    user.setPassword(userService.encodeUserPassword(input.getPassword()));
    return userRepository.save(user);
  }
}
