package io.openaev.service;

import static io.openaev.config.security.SecurityService.OPENAEV_PROVIDER_PATH_PREFIX;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.sso.GroupMapping;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
  private static final ObjectMapper mapper = new ObjectMapper();

  private final GroupRepository groupRepository;
  private final TenantRepository tenantRepository;
  private final Environment env;
  public static final String ROLES_PATH_SUFFIX = "roles_path";
  public static final String GROUPS_PATH_SUFFIX = "groups_path";

  public void mapCurrentUserWithGroup(String property, User user, List<String> groupsFromToken) {
    List<GroupMapping> groupMappings = safeParseMappings(property);

    for (GroupMapping mapping : groupMappings) {
      String idpGroup = mapping.getIdpGroup();
      String userGroup = mapping.getUserGroup();
      boolean autoCreate = mapping.isAutoCreate();
      if (groupsFromToken.contains(idpGroup)) {
        Optional<Group> groupOptional = groupRepository.findByName(userGroup);
        if (groupOptional.isPresent()) {
          List<Group> userGroups = user.getUnscopedGroups();
          boolean alreadyAssigned =
              userGroups.stream()
                  .anyMatch(userG -> userG.getName().equals(groupOptional.get().getName()));
          if (!alreadyAssigned) {
            userGroups.add(groupOptional.get());
            user.setGroups(userGroups);
          }
        } else {
          if (autoCreate) {
            Group newGroup = new Group();
            newGroup.setName(userGroup);
            groupRepository.save(newGroup);
            List<Group> userGroups = user.getUnscopedGroups();
            userGroups.add(newGroup);
            user.setGroups(userGroups);
          } else {
            log.error(
                "Group '{}' not found in database and autoCreate is disabled for mapping '{}'",
                userGroup,
                idpGroup);
          }
        }
        attachTenantFromGroupMapping(mapping, user);
      }

      // If the user no longer has this group in the token but still has it assigned,
      // remove it — the user was removed from the group in the identity provider
      if (!groupsFromToken.contains(idpGroup)
          && user.getUnscopedGroups().stream()
              .anyMatch(groupOfUser -> groupOfUser.getName().equals(mapping.getUserGroup()))) {
        List<Group> userGroups = user.getUnscopedGroups();
        userGroups.removeIf(group -> group.getName().equals(mapping.getUserGroup()));
        user.setGroups(userGroups);
      }
    }

    // Log token groups that have no configured mapping — DEBUG level because this is
    // expected behavior (users often belong to more IDP groups than are mapped)
    Set<String> mappedIdpGroups =
        groupMappings.stream().map(GroupMapping::getIdpGroup).collect(Collectors.toSet());
    for (String tokenGroup : groupsFromToken) {
      if (!mappedIdpGroups.contains(tokenGroup)) {
        log.debug("Token group '{}' has no configured mapping — skipping", tokenGroup);
      }
    }
  }

  private static List<GroupMapping> safeParseMappings(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return mapper.readValue(json, new TypeReference<>() {});
    } catch (IOException e) {
      // Log and return empty list instead of throwing
      log.error("Failed to parse group mappings: {}", e.getMessage(), e);
      return List.of();
    }
  }

  /**
   * Attaches the user to the tenant configured in the group mapping, if any. Skips if tenantId is
   * not set, the user is already attached, or the tenant is not found.
   */
  private void attachTenantFromGroupMapping(GroupMapping mapping, User user) {
    String tenantId = mapping.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      return;
    }
    boolean alreadyAttached = user.getTenants().stream().anyMatch(t -> t.getId().equals(tenantId));
    if (alreadyAttached) {
      return;
    }
    if (!tenantRepository.existsById(tenantId)) {
      log.warn("Group mapping tenant ID '{}' configured but not found in database", tenantId);
      return;
    }
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    user.getTenants().add(tenant);
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
