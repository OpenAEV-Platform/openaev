package io.openaev.service;

import static io.openaev.config.security.SecurityService.OPENAEV_PROVIDER_PATH_PREFIX;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.sso.GroupMapping;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class UserMappingService {

  private final GroupRepository groupRepository;
  private final Environment env;
  public static final String ROLES_PATH_SUFFIX = "roles_path";
  public static final String GROUPS_PATH_SUFFIX = "groups_path";

  public void mapCurrentUserWithGroup(String property, User user, List<String> groupsFromToken) {
    List<GroupMapping> groupMappings = safeParseMappings(property);
    for (GroupMapping mapping : groupMappings) {
      String idpGroup = mapping.getIdpGroup();
      String userGroup = mapping.getUserGroup();
      boolean autoCreate = mapping.isAutoCreate();
      for (String role : groupsFromToken) {
        if (idpGroup.equals(role)) {
          Optional<Group> groupOptional = groupRepository.findByName(userGroup);
          if (groupOptional.isPresent()) {
            List<Group> userGroups = user.getGroups();
            List<Group> existing =
                userGroups.stream()
                    .filter(userG -> userG.getName().equals(groupOptional.get().getName()))
                    .toList();
            if (existing.isEmpty()) {
              userGroups.add(groupOptional.get());
              user.setGroups(userGroups);
            }
          } else {
            if (autoCreate) {
              Group newGroup = new Group();
              newGroup.setName(userGroup);
              groupRepository.save(newGroup);
              List<Group> userGroups = user.getGroups();
              userGroups.add(newGroup);
              user.setGroups(userGroups);
            } else {
              log.error("Did not create new group");
            }
          }
        } else {
          log.error(String.format("No corresponding group found for group %s", role));
        }
      }

      // If the user has not this group in the groups from the token but he has the group in his
      // current groups
      if (groupsFromToken.stream().noneMatch(groupToken -> groupToken.equals(mapping.getIdpGroup()))
          && user.getGroups().stream()
              .anyMatch(groupOfUser -> groupOfUser.getName().equals(mapping.getUserGroup()))) {
        // It means the user was removed from the group in the identity provider -> we remove it
        // from its current groups
        List<Group> userGroups = user.getGroups();
        userGroups.removeIf(group -> group.getName().equals(mapping.getUserGroup()));
        user.setGroups(userGroups);
      }
    }
  }

  private static List<GroupMapping> safeParseMappings(String json) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(json, new TypeReference<List<GroupMapping>>() {});
    } catch (IOException e) {
      // Log and return empty list instead of throwing
      System.err.println("Failed to parse mappings: " + e.getMessage());
      return List.of();
    }
  }

  /**
   * Extract the roles from a user
   *
   * @param user an authenticated user. For now, we only support Saml2AuthenticatedPrincipal or
   *     OAuth2User
   * @param registrationId the provider
   * @return the list of roles from the user
   */
  public List<String> extractRolesFromUser(
      @NotNull final AuthenticatedPrincipal user, @NotBlank final String registrationId) {
    return extractAttributesListFromUser(user, registrationId, ROLES_PATH_SUFFIX);
  }

  /**
   * Extract the groups from a user
   *
   * @param user an authenticated user. For now, we only support Saml2AuthenticatedPrincipal or
   *     OAuth2User
   * @param registrationId the provider
   * @return the list of groups from the user
   */
  public List<String> extractGroupsFromUser(
      @NotNull final AuthenticatedPrincipal user, @NotBlank final String registrationId) {
    return extractAttributesListFromUser(user, registrationId, GROUPS_PATH_SUFFIX);
  }

  /**
   * Extract an attributes and return a list of values
   *
   * @param user the user to use
   * @param registrationId the provider
   * @param property the property we want to extract
   * @return a list of values
   */
  private List<String> extractAttributesListFromUser(
      @NotNull final AuthenticatedPrincipal user,
      @NotBlank final String registrationId,
      @NotBlank final String property) {
    List<String> attributePaths = getProviderProperty(registrationId, property);
    List<String> extractedValues = new ArrayList<>();

    for (String path : attributePaths) {
      List<String> roles = getAttributeOfUser(user, path);

      if (roles != null) {
        extractedValues.addAll(roles);
      }
    }
    return extractedValues;
  }

  /**
   * Get the attribute from a user depending on it's type
   *
   * @param user an AuthenticatedPrincipal. We only support Saml2AuthenticatedPrincipal and
   *     OAuth2User
   * @param path the path of the attribute
   * @return the list of corresponding values
   */
  private List<String> getAttributeOfUser(
      @NotNull final AuthenticatedPrincipal user, @NotBlank final String path) {
    if (user instanceof Saml2AuthenticatedPrincipal) {
      return ((Saml2AuthenticatedPrincipal) user).getAttribute(path);
    } else if (user instanceof OAuth2User) {
      return ((OAuth2User) user).getAttribute(path);
    } else {
      throw new NotImplementedException("Login with this type of user is not implemented");
    }
  }

  /**
   * Get a property for a provider
   *
   * @param registrationId the provider
   * @param property the property
   * @return the value of the property
   */
  private List<String> getProviderProperty(
      @NotBlank final String registrationId, final String property) {
    String rolesPathConfig = OPENAEV_PROVIDER_PATH_PREFIX + registrationId + "." + property;
    //noinspection unchecked
    return env.getProperty(rolesPathConfig, List.class, new ArrayList<String>());
  }
}
