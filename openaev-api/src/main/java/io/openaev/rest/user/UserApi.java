package io.openaev.rest.user;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.config.SessionManager;
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
import io.openaev.service.MailingService;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import io.openaev.utils.RandomUtils;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
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
  private static final long tenMinutes = 1000L * 60L * 10L;

  @Resource private SessionManager sessionManager;
  private final UserRepository userRepository;
  private final UserService userService;
  private final MailingService mailingService;
  private final RandomUtils randomUtils;
  private final UserEventService userEventService;

  private final Map<String, String> resetTokenMap = new PassiveExpiringMap<>(tenMinutes);

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
    Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(input.getLogin());
    // always compute a random value to reduce gap in time
    // spent between user found and user not found branches
    // note: we still spend more time in the "user found" branch
    // due to sending an email
    String resetToken = randomUtils.getRandomAlphanumeric(64);
    if (optionalUser.isPresent()) {
      User user = optionalUser.get();
      String username = user.getName() != null ? user.getName() : user.getEmail();
      if ("fr".equals(input.getLang())) {
        String subject = "Code de récupération OpenAEV: " + resetToken;
        String body =
            "Bonjour "
                + username
                + ",</br>"
                + "Nous avons reçu une demande de réinitialisation de votre mot de passe OpenAEV.</br>"
                + "Entrez le code de réinitialisation du mot de passe suivant : "
                + resetToken;
        mailingService.sendEmail(subject, body, List.of(user));
      } else {
        String subject = "OpenAEV account recovery code: " + resetToken;
        String body =
            "Hi "
                + username
                + ",</br>"
                + "A request has been made to reset your OpenAEV password.</br>"
                + "Enter the following password recovery code: "
                + resetToken;
        mailingService.sendEmail(subject, body, List.of(user));
      }
      // Store in memory reset token
      synchronized (resetTokenMap) {
        resetTokenMap.put(user.getId(), resetToken);
      }
    }
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
    String userId = null;
    synchronized (resetTokenMap) {
      for (Map.Entry<String, String> entry : resetTokenMap.entrySet()) {
        // Use token.equals() to handle null values from expired entries in PassiveExpiringMap
        if (token.equals(entry.getValue())) {
          userId = entry.getKey(); // don't break out
        }
      }
    }
    if (userId != null) {
      String password = input.getPassword();
      String passwordValidation = input.getPasswordValidation();
      if (!passwordValidation.equals(password)) {
        throw new InputValidationException("password_validation", "Bad password validation");
      }
      User changeUser = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
      changeUser.setPassword(userService.encodeUserPassword(password));
      User savedUser = userRepository.save(changeUser);
      synchronized (resetTokenMap) {
        resetTokenMap.remove(userId);
      }
      return savedUser;
    }
    // Bad token or expired token
    throw new AccessDeniedException("Invalid credentials");
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
    return resetTokenMap.get(token) != null;
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
