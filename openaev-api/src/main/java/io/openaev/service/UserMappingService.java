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
  private final TenantRepository tenantRepository;
  private final Environment env;
  public static final String ROLES_PATH_SUFFIX = "roles_path";
  public static final String GROUPS_PATH_SUFFIX = "groups_path";
  public static final String TENANT_ID_SUFFIX = ".tenant_id";

  /**
   * Maps a user to OpenAEV groups based on SSO group mapping configuration. Resolves the tenant
   * from the provider's {@code tenant_id} property (e.g. {@code
   * openaev.provider.microsoft.tenant_id}). If a mapping entry has a {@code tenantId} field, it
   * takes precedence over the provider-level tenant.
   */
  public void mapCurrentUserWithGroup(
      String property, String registrationId, User user, List<String> groupsFromToken) {
    log.info(
        "SSO group mapping — user: {}, groupsFromToken: {}, mappingConfig: {}",
        user.getEmail(),
        groupsFromToken,
        property);

    String providerTenantId = resolveProviderTenantId(registrationId);
    List<GroupMapping> groupMappings = safeParseMappings(property);

    for (GroupMapping mapping : groupMappings) {
      String idpGroup = mapping.getIdpGroup();
      String userGroup = mapping.getUserGroup();
      boolean autoCreate = mapping.isAutoCreate();
      // Mapping-level tenantId takes precedence; fall back to provider-level tenant_id
      // in case we define in properties file both:
      // openaev.provider.microsoft.tenant_id=my-tenant
      // openaev.provider.microsoft.groups_management=
      // [{"idpGroup":"Filigran","userGroup":"GROUP_A","autoCreate":true,"tenantId":"some-other-tenant-uuid"}]
      // some-other-tenant-uuid takes precedence other my-tenant
      String tenantId =
          (mapping.getTenantId() != null && !mapping.getTenantId().isBlank())
              ? mapping.getTenantId()
              : providerTenantId;

      for (String role : groupsFromToken) {
        if (idpGroup.equals(role)) {
          Optional<Group> groupOptional = findGroupByNameScoped(userGroup, tenantId);
          if (groupOptional.isPresent()) {
            List<Group> userGroups = user.getUnscopedGroups();
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
              if (tenantId != null && !tenantId.isBlank()) {
                newGroup.setTenant(tenantRepository.getReferenceById(tenantId));
              }
              groupRepository.save(newGroup);
              List<Group> userGroups = user.getUnscopedGroups();
              userGroups.add(newGroup);
              user.setGroups(userGroups);
              log.info("Auto-created group '{}' in tenant '{}'", userGroup, tenantId);
            } else {
              log.warn(
                  "Group '{}' not found and autoCreate is false — skipping mapping", userGroup);
            }
          }
          attachTenantToUser(tenantId, user);
        } else {
          log.debug("IdP group '{}' does not match token group '{}' — skipping", idpGroup, role);
        }
      }

      // If the user has not this group in the groups from the token but he has the group in his
      // current groups, it means the user was removed from the group in the identity provider.
      // Only remove if NONE of the mappings targeting this userGroup have a matching idpGroup
      // in the token — otherwise a different mapping entry may have just added it.
      boolean anyMappingMatchesForSameGroup =
          groupMappings.stream()
              .filter(m -> m.getUserGroup().equals(mapping.getUserGroup())) // do we have several mapping with same group
              .anyMatch(m -> groupsFromToken.contains(m.getIdpGroup()));
      if (!anyMappingMatchesForSameGroup
          && user.getUnscopedGroups().stream()
              .anyMatch(groupOfUser -> groupOfUser.getName().equals(mapping.getUserGroup()))) {
        // It means the user was removed from the group in the identity provider -> we remove it
        // from its current groups
        List<Group> userGroups = user.getUnscopedGroups();
        userGroups.removeIf(group -> group.getName().equals(mapping.getUserGroup()));
        user.setGroups(userGroups);
      }
    }
  }

  private static List<GroupMapping> safeParseMappings(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(json, new TypeReference<>() {});
    } catch (IOException e) {
      // Log and return empty list instead of throwing
      log.error("Failed to parse group mappings: {}", e.getMessage(), e);
      return List.of();
    }
  }

  /** Resolves the tenant ID from the provider configuration property. */
  private String resolveProviderTenantId(String registrationId) {
    if (registrationId == null || registrationId.isBlank()) {
      return null;
    }
    return env.getProperty(
        OPENAEV_PROVIDER_PATH_PREFIX + registrationId + TENANT_ID_SUFFIX, String.class, "");
  }

  /** Finds a group by name, scoped to a tenant if tenantId is provided. */
  private Optional<Group> findGroupByNameScoped(String groupName, String tenantId) {
    if (tenantId != null && !tenantId.isBlank()) {
      return groupRepository.findByNameAndTenantId(groupName, tenantId);
    }
    return groupRepository.findByName(groupName);
  }

  /**
   * Attaches the user to the given tenant, if not already attached. Skips if tenantId is null/blank
   * or the tenant is not found.
   */
  private void attachTenantToUser(String tenantId, User user) {
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
