package io.openbas.rest.user;

import static io.openbas.database.model.User.ROLE_ADMIN;
import static io.openbas.database.specification.UserSpecification.fromIds;
import static io.openbas.helper.DatabaseHelper.updateRelation;
import static io.openbas.helper.StreamHelper.iterableToSet;

import io.openbas.aop.UserRoleDescription;
import io.openbas.config.SessionManager;
import io.openbas.database.model.User;
import io.openbas.database.raw.RawUser;
import io.openbas.database.repository.OrganizationRepository;
import io.openbas.database.repository.TagRepository;
import io.openbas.database.repository.UserRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.exception.InputValidationException;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.user.form.login.LoginUserInput;
import io.openbas.rest.user.form.login.ResetUserInput;
import io.openbas.rest.user.form.user.ChangePasswordInput;
import io.openbas.rest.user.form.user.CreateUserInput;
import io.openbas.rest.user.form.user.UpdateUserInput;
import io.openbas.rest.user.form.user.UserOutput;
import io.openbas.rest.user.service.UserCriteriaBuilderService;
import io.openbas.service.MailingService;
import io.openbas.service.UserService;
import io.openbas.utils.pagination.SearchPaginationInput;
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
import java.util.Optional;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@UserRoleDescription
@Tag(
    name = "Users management",
    description = "Endpoints to manage users",
    externalDocs =
        @ExternalDocumentation(
            description = "Documentation about users",
            url = "https://docs.openbas.io/latest/administration/users/"))
public class UserApi extends RestBehavior {

  public static final String USER_URI = "/api/users";

  PassiveExpiringMap<String, String> resetTokenMap = new PassiveExpiringMap<>(1000 * 60 * 10);
  @Resource private SessionManager sessionManager;
  private OrganizationRepository organizationRepository;
  private UserRepository userRepository;
  private TagRepository tagRepository;
  private UserService userService;
  private MailingService mailingService;
  private UserCriteriaBuilderService userCriteriaBuilderService;

  @Autowired
  public void setMailingService(MailingService mailingService) {
    this.mailingService = mailingService;
  }

  @Autowired
  public void setTagRepository(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  @Autowired
  public void setOrganizationRepository(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  @Autowired
  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setUserRepository(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Autowired
  public void setUserCriteriaBuilderService(UserCriteriaBuilderService userCriteriaBuilderService) {
    this.userCriteriaBuilderService = userCriteriaBuilderService;
  }

  @Operation(description = "Endpoint to login", summary = "Endpoint to login")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(schema = @Schema(implementation = User.class))),
      })
  @PostMapping("/api/login")
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
  public ResponseEntity<?> passwordReset(@Valid @RequestBody ResetUserInput input) {
    Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(input.getLogin());
    if (optionalUser.isPresent()) {
      User user = optionalUser.get();
      String resetToken = RandomStringUtils.randomNumeric(8);
      String username = user.getName() != null ? user.getName() : user.getEmail();
      if ("fr".equals(input.getLang())) {
        String subject = resetToken + " est votre code de récupération de compte OpenBAS";
        String body =
            "Bonjour "
                + username
                + ",</br>"
                + "Nous avons reçu une demande de réinitialisation de votre mot de passe OpenBAS.</br>"
                + "Entrez le code de réinitialisation du mot de passe suivant : "
                + resetToken;
        mailingService.sendEmail(subject, body, List.of(user));
      } else {
        String subject = resetToken + " is your recovery code of your OpenBAS account";
        String body =
            "Hi "
                + username
                + ",</br>"
                + "A request has been made to reset your OpenBAS password.</br>"
                + "Enter the following password recovery code: "
                + resetToken;
        mailingService.sendEmail(subject, body, List.of(user));
      }
      // Store in memory reset token
      resetTokenMap.put(resetToken, user.getId());
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.badRequest().build();
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
  public User changePasswordReset(
      @PathVariable @Schema(description = "Token generated during reset") String token,
      @Valid @RequestBody ChangePasswordInput input)
      throws InputValidationException {
    String userId = resetTokenMap.get(token);
    if (userId != null) {
      String password = input.getPassword();
      String passwordValidation = input.getPasswordValidation();
      if (!passwordValidation.equals(password)) {
        throw new InputValidationException("password_validation", "Bad password validation");
      }
      User changeUser = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
      changeUser.setPassword(userService.encodeUserPassword(password));
      User savedUser = userRepository.save(changeUser);
      resetTokenMap.remove(token);
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
  public boolean validatePasswordResetToken(
      @PathVariable @Schema(description = "Token generated during reset") String token) {
    return resetTokenMap.get(token) != null;
  }

  @Operation(description = "List all the users", summary = "List users")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of users")})
  @Secured(ROLE_ADMIN)
  @GetMapping("/api/users")
  public List<RawUser> users() {
    return userRepository.rawAll();
  }

  @Operation(
      description = "Search the users corresponding to the criteria",
      summary = "Search users")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of users")})
  @PostMapping(USER_URI + "/search")
  public Page<UserOutput> users(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.userCriteriaBuilderService.userPagination(searchPaginationInput);
  }

  @Operation(description = "Find a list of users based on their ids", summary = "Find users")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of users")})
  @PostMapping(USER_URI + "/find")
  @Transactional(readOnly = true)
  public List<UserOutput> findUsers(
      @RequestBody @Valid @NotNull @Parameter(description = "List of ids")
          final List<String> userIds) {
    return this.userCriteriaBuilderService.find(fromIds(userIds));
  }

  @Secured(ROLE_ADMIN)
  @PutMapping("/api/users/{userId}/password")
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

  @Secured(ROLE_ADMIN)
  @PostMapping("/api/users")
  @Transactional(rollbackFor = Exception.class)
  @Operation(description = "Create a new user", summary = "Create user")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The new user")})
  public User createUser(@Valid @RequestBody CreateUserInput input) {
    return userService.createUser(input, 1);
  }

  @Secured(ROLE_ADMIN)
  @PutMapping("/api/users/{userId}")
  @Transactional(rollbackFor = Exception.class)
  @Operation(description = "Update a user", summary = "Update user")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modified user")})
  public User updateUser(
      @PathVariable @Schema(description = "ID of the user") String userId,
      @Valid @RequestBody UpdateUserInput input) {
    User user = userRepository.findById(userId).orElseThrow(ElementNotFoundException::new);
    user.setUpdateAttributes(input);
    user.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    User savedUser = userRepository.save(user);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
  }

  @Secured(ROLE_ADMIN)
  @DeleteMapping("/api/users/{userId}")
  @Transactional(rollbackFor = Exception.class)
  @Operation(description = "Delete a user", summary = "Delete user")
  @ApiResponses(value = {@ApiResponse(responseCode = "200")})
  public void deleteUser(@PathVariable @Schema(description = "ID of the user") String userId) {
    sessionManager.invalidateUserSession(userId);
    userRepository.deleteById(userId);
  }
}
