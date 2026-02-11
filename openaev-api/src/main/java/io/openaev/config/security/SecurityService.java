package io.openaev.config.security;

import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.user.CreateUserInput;
import io.openaev.service.UserMappingService;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityService {

  public static final String OPENAEV_PROVIDER_PATH_PREFIX = "openaev.provider.";
  public static final String ROLES_ADMIN_PATH_SUFFIX = ".roles_admin";
  public static final String ROLES_IDP_MAP_PATH_SUFFIX = ".roles_idp_map";
  public static final String ALL_ADMIN_PATH_SUFFIX = ".all_admin";
  public static final String AUDIENCE_PATH = ".audience";
  public static final String REGISTRATION_ID = "registration_id";

  private final UserRepository userRepository;
  private final UserService userService;
  private final UserMappingService userMappingService;
  private final Environment env;
  private final UserEventService userEventService;

  public User userManagement(
      String emailAttribute,
      String registrationId,
      List<String> rolesFromToken,
      String firstName,
      String lastName) {
    String email = ofNullable(emailAttribute).orElseThrow();
    List<String> adminRoles = getAdminRoles(registrationId);
    boolean allAdmin = isAllAdmin(registrationId);
    boolean isAdmin = allAdmin || adminRoles.stream().anyMatch(rolesFromToken::contains);
    if (hasLength(email)) {
      Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(email);
      // If user not exists, create it
      if (optionalUser.isEmpty()) {
        CreateUserInput createUserInput = new CreateUserInput();
        createUserInput.setEmail(email);
        createUserInput.setFirstname(firstName);
        createUserInput.setLastname(lastName);
        if (allAdmin || !adminRoles.isEmpty()) {
          createUserInput.setAdmin(isAdmin);
        }
        User user = this.userService.createUser(createUserInput, 0);
        this.userEventService.createUserCreatedEvent(user, registrationId);
        userEventService.createLoginSuccessEvent(user);
        String rolesIDPMap =
            OPENAEV_PROVIDER_PATH_PREFIX + registrationId + ROLES_IDP_MAP_PATH_SUFFIX;
        userMappingService.mapCurrentUserWithGroup(rolesIDPMap,user,rolesFromToken);
        return user;
        //MappingService
      } else {
        // If user exists, update it
        User currentUser = optionalUser.get();
        currentUser.setFirstname(firstName);
        currentUser.setLastname(lastName);
        if (allAdmin || !adminRoles.isEmpty()) {
          currentUser.setAdmin(isAdmin);
        }
        userEventService.createLoginSuccessEvent(currentUser);
        String rolesIDPMap =
            OPENAEV_PROVIDER_PATH_PREFIX + registrationId + ROLES_IDP_MAP_PATH_SUFFIX;
        userMappingService.mapCurrentUserWithGroup(rolesIDPMap,currentUser,rolesFromToken);
        return this.userService.updateUser(currentUser);
      }
    }
    /*
    classe  MappingService
    rolesFromToken c'est la liste des rôles côté IDP
    mapRole = roles_IDP_map
        parsedRole = mapRole.parse ({IDPRole: ,OAEVGroup:  ,autoCreate: })
        role = parsedRole.IDPRole
        currentUserIDProle = IDP_claim_role //rolesFromToken
        if (role === currentUserIDProle) { // à faire pour chaque élément de rolesFromToken
          groupRepository.findByName(OAEVGroup) ?
          {
            currentUser.setGroup (OAEVGroup)
          } : {
              if (autocreate){
                groupRepository.save(role)
                currentUser.setGroup(role)
              }
          }

        }
        Prevoir des logs pour les else
        */
    return null;
  }

  // -- UTILS --

  public String getAudience(@NotBlank final String registrationId) {
    String rolesPathConfig = OPENAEV_PROVIDER_PATH_PREFIX + registrationId + AUDIENCE_PATH;
    return env.getProperty(rolesPathConfig, String.class, "");
  }

  // -- PRIVATE --

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
