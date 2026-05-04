package io.openaev.config.security;

import static io.jsonwebtoken.lang.Strings.hasText;
import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.UserMappingService;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityService {

  public static final String OPENAEV_PROVIDER_PATH_PREFIX = "openaev.provider.";
  public static final String ROLES_ADMIN_PATH_SUFFIX = ".roles_admin";
  public static final String GROUPS_MANAGEMENT_SUFFIX = ".groups_management";
  public static final String ALL_ADMIN_PATH_SUFFIX = ".all_admin";
  public static final String AUDIENCE_PATH = ".audience";
  public static final String TENANT_ID_SUFFIX = ".tenant_id";
  public static final String REGISTRATION_ID = "registration_id";

  private final UserRepository userRepository;
  private final UserService userService;
  private final UserMappingService userMappingService;
  private final Environment env;
  private final UserEventService userEventService;
  private final TenantRepository tenantRepository;

  public User userManagement(
      String emailAttribute,
      String registrationId,
      List<String> roles,
      List<String> groups,
      String firstName,
      String lastName) {
    String email = ofNullable(emailAttribute).orElseThrow();
    List<String> adminRoles = getAdminRoles(registrationId);
    boolean allAdmin = isAllAdmin(registrationId);
    boolean isAdmin = allAdmin || adminRoles.stream().anyMatch(roles::contains);
    if (hasLength(email)) {
      Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(email);
      // If user not exists, create it
      if (optionalUser.isEmpty()) {
        User user =
            this.userService.createInternalUser(
                email, firstName, lastName, isAdmin, UUID.randomUUID().toString());
        this.userEventService.createUserCreatedEvent(user, registrationId);
        userEventService.createLoginSuccessEvent(user);
        String groupsManagementObject =
            env.getProperty(
                OPENAEV_PROVIDER_PATH_PREFIX + registrationId + GROUPS_MANAGEMENT_SUFFIX,
                String.class,
                "");
        userMappingService.mapCurrentUserWithGroup(groupsManagementObject, user, groups);
        attachTenant(registrationId, user);
        return this.userService.saveUser(user);
      } else {
        // If user exists, update it
        User currentUser = optionalUser.get();
        currentUser.setFirstname(firstName);
        currentUser.setLastname(lastName);
        if (allAdmin || !adminRoles.isEmpty()) {
          currentUser.setAdmin(isAdmin);
        }
        userEventService.createLoginSuccessEvent(currentUser);
        String groupsManagementObject =
            env.getProperty(
                OPENAEV_PROVIDER_PATH_PREFIX + registrationId + GROUPS_MANAGEMENT_SUFFIX,
                String.class,
                "");
        userMappingService.mapCurrentUserWithGroup(groupsManagementObject, currentUser, groups);
        attachTenant(registrationId, currentUser);
        return this.userService.saveUser(currentUser);
      }
    }
    return null;
  }

  // -- UTILS --

  public String getAudience(@NotBlank final String registrationId) {
    String rolesPathConfig = OPENAEV_PROVIDER_PATH_PREFIX + registrationId + AUDIENCE_PATH;
    return env.getProperty(rolesPathConfig, String.class, "");
  }

  // -- PRIVATE --

  /** Attaches the user to the tenant configured for the given SSO provider registration. */
  private void attachTenant(String registrationId, User user) {
    String tenantId =
        env.getProperty(
            OPENAEV_PROVIDER_PATH_PREFIX + registrationId + TENANT_ID_SUFFIX, String.class, "");
    if (!hasText(tenantId)) {
      return;
    }
    boolean alreadyAttached = user.getTenants().stream().anyMatch(t -> t.getId().equals(tenantId));
    if (alreadyAttached) {
      return;
    }
    if (!tenantRepository.existsById(tenantId)) {
      log.warn("SSO tenant ID '{}' configured but not found in database", tenantId);
      return;
    }
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    user.getTenants().add(tenant);
  }

  private List<String> getAdminRoles(@NotBlank final String registrationId) {
    String rolesAdminConfig =
        OPENAEV_PROVIDER_PATH_PREFIX + registrationId + ROLES_ADMIN_PATH_SUFFIX;
    //noinspection unchecked
    return this.env.getProperty(rolesAdminConfig, List.class, new ArrayList<String>());
  }

  private Boolean isAllAdmin(@NotBlank final String registrationId) {
    String allAdminConfig = OPENAEV_PROVIDER_PATH_PREFIX + registrationId + ALL_ADMIN_PATH_SUFFIX;
    return this.env.getProperty(allAdminConfig, Boolean.class, false);
  }
}
