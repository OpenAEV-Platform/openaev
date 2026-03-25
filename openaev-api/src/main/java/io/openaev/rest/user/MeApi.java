package io.openaev.rest.user;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.specification.TokenSpecification.fromUser;
import static io.openaev.helper.DatabaseHelper.updateRelation;

import io.openaev.aop.AccessControl;
import io.openaev.api.tenants.TenantMapper;
import io.openaev.api.tenants.TenantOutput;
import io.openaev.config.SessionManager;
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
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MeApi extends RestBehavior {

  public static final String ME_URI = "/api/me";

  private final SessionManager sessionManager;
  private final OrganizationRepository organizationRepository;
  private final TokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final TenantService tenantService;

  @GetMapping("/api/logout")
  @AccessControl(skipRBAC = true)
  public ResponseEntity<Object> logout() {
    return ResponseEntity.ok().build();
  }

  @GetMapping(ME_URI)
  @AccessControl(skipRBAC = true)
  public User me() {
    return userRepository
        .findById(currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
  }

  @PutMapping(ME_URI + "/profile")
  @AccessControl(skipRBAC = true)
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
  @AccessControl(skipRBAC = true)
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
  @AccessControl(skipRBAC = true)
  public User updatePassword(@Valid @RequestBody UpdateMePasswordInput input)
      throws InputValidationException {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    if (userService.isUserPasswordValid(user, input.getCurrentPassword())) {
      user.setPassword(userService.encodeUserPassword(input.getPassword()));
      return userRepository.save(user);
    } else {
      throw new InputValidationException("user_current_password", "Bad current password");
    }
  }

  @PostMapping(ME_URI + "/token/refresh")
  @AccessControl(skipRBAC = true)
  @Transactional(rollbackOn = Exception.class)
  public Token renewToken(@Valid @RequestBody RenewTokenInput input) {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    Token token =
        tokenRepository.findById(input.getTokenId()).orElseThrow(ElementNotFoundException::new);
    if (!user.equals(token.getUser())) {
      throw new AccessDeniedException("You are not allowed to renew this token");
    }
    token.setValue(UUID.randomUUID().toString());
    return tokenRepository.save(token);
  }

  @GetMapping(ME_URI + "/tenants")
  @AccessControl(skipRBAC = true)
  public List<TenantOutput> myTenants() {
    return tenantService.findTenantsByUserId(currentUser().getId()).stream()
        .map(TenantMapper::toOutput)
        .toList();
  }

  @GetMapping(ME_URI + "/tokens")
  @AccessControl(skipRBAC = true)
  public List<Token> tokens() {
    return tokenRepository.findAll(fromUser(currentUser().getId()));
  }
}
