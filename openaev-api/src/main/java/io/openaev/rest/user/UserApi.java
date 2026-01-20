package io.openaev.rest.user;

import static io.openaev.database.specification.UserSpecification.fromIds;

import io.openaev.aop.RBAC;
import io.openaev.aop.UserRoleDescription;
import io.openaev.config.SessionManager;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawUser;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.rest.user.form.login.ResetUserInput;
import io.openaev.rest.user.form.user.ChangePasswordInput;
import io.openaev.rest.user.form.user.CreateUserInput;
import io.openaev.rest.user.form.user.UpdateUserInput;
import io.openaev.rest.user.form.user.UserOutput;
import io.openaev.rest.user.service.UserCriteriaBuilderService;
import io.openaev.service.MailingService;
import io.openaev.service.UserService;
import io.openaev.utils.RandomUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.data.domain.Page;
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
  private final UserCriteriaBuilderService userCriteriaBuilderService;
  private final RandomUtils randomUtils;

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
  @RBAC(skipRBAC = true)
  @UserRoleDescription(needAuthenticated = false)
  public User login(@Valid @RequestBody LoginUserInput input) {
    Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(input.getLogin());
    if (optionalUser.isPresent()) {
      User user = optionalUser.get();
      if (userService.isUserPasswordValid(user, input.getPassword())) {
        userService.createUserSession(user);
        return user;
      }
    }
    throw new BadCredentialsException("Invalid credential.");
  }

  @Operation(description = "Reset the password", summary = "Password reset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Mail to reset the password sent"),
        @ApiResponse(responseCode = "400", description = "The user was not found")
      })
  @PostMapping("/api/reset")
  @RBAC(skipRBAC = true)
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
  @RBAC(skipRBAC = true)
  public User changePasswordReset(
      @PathVariable @Schema(description = "Token generated during reset") String token,
      @Valid @RequestBody ChangePasswordInput input)
      throws InputValidationException {
    String userId = null;
    synchronized (resetTokenMap) {
      for (Map.Entry<String, String> entry : resetTokenMap.entrySet()) {
        if (entry.getValue().equals(token)) {
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
  @RBAC(skipRBAC = true)
  public boolean validatePasswordResetToken(
      @PathVariable @Schema(description = "Token generated during reset") String token) {
    return resetTokenMap.get(token) != null;
  }

  @Operation(description = "List all the users", summary = "List users")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of users")})
  @GetMapping("/api/users")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.USER)
  public List<RawUser> users() {
    return userRepository.rawAll();
  }

  @Operation(
      description = "Search the users corresponding to the criteria",
      summary = "Search users")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of users")})
  @PostMapping(USER_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER)
  public Page<UserOutput> users(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.userCriteriaBuilderService.userPagination(searchPaginationInput);
  }

  @Operation(description = "Find a list of users based on their ids", summary = "Find users")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of users")})
  @PostMapping(USER_URI + "/find")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER)
  @Transactional(readOnly = true)
  public List<UserOutput> findUsers(
      @RequestBody @Valid @NotNull @Parameter(description = "List of ids")
          final List<String> userIds) {
    return this.userCriteriaBuilderService.find(fromIds(userIds));
  }

  @PutMapping("/api/users/{userId}/password")
  @RBAC(resourceId = "#userId", actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
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

  @PostMapping("/api/users")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.USER)
  @Transactional(rollbackFor = Exception.class)
  @Operation(description = "Create a new user", summary = "Create user")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The new user")})
  public User createUser(@Valid @RequestBody CreateUserInput input) {
    return userService.createUser(input, 1);
  }

  @PutMapping("/api/users/{userId}")
  @RBAC(resourceId = "#userId", actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  @Transactional(rollbackFor = Exception.class)
  @Operation(description = "Update a user", summary = "Update user")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modified user")})
  public User updateUser(
      @PathVariable @Schema(description = "ID of the user") String userId,
      @Valid @RequestBody UpdateUserInput input) {
    return userService.updateUser(userId, input);
  }

  @DeleteMapping("/api/users/{userId}")
  @RBAC(resourceId = "#userId", actionPerformed = Action.DELETE, resourceType = ResourceType.USER)
  @Transactional(rollbackFor = Exception.class)
  @Operation(description = "Delete a user", summary = "Delete user")
  @ApiResponses(value = {@ApiResponse(responseCode = "200")})
  public void deleteUser(@PathVariable @Schema(description = "ID of the user") String userId) {
    sessionManager.invalidateUserSession(userId);
    userRepository.deleteById(userId);
  }
}
