package io.openaev.service;

import static io.openaev.config.security.SecurityService.OPENAEV_PROVIDER_PATH_PREFIX;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.sso.GroupMapping;
import jakarta.annotation.Nonnull;
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
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  public static final String ROLES_PATH_SUFFIX = "roles_path";
  public static final String GROUPS_PATH_SUFFIX = "groups_path";

  /**
   * Reconciles the user's groups against the mappings' idp-group -&gt; user-group config, adding or
   * removing memberships to match the token. Runs on every SSO login, so a group can be silently
   * revoked here on a login that follows an idp-side removal — not just via the admin UI.
   *
   * <p>Evicts the user's marking clearance cache whenever a group is actually added or removed:
   * this is a {@code users_groups} write like any other, and a stale cached clearance can only be
   * too permissive, never too strict.
   */
  public void mapCurrentUserWithGroup(String property, User user, List<String> groupsFromToken) {
    List<GroupMapping> groupMappings = safeParseMappings(property);
    boolean groupsChanged = false;

    for (GroupMapping mapping : groupMappings) {
      String tenantId = hasText(mapping.getTenantId()) ? mapping.getTenantId() : null;
      if (tenantId != null && !tenantRepository.existsById(tenantId)) {
        log.warn("Group mapping tenant ID '{}' configured but not found in database", tenantId);
        continue;
      }
      groupsChanged |=
          groupsFromToken.contains(mapping.getIdpGroup())
              ? grantGroup(user, mapping, tenantId)
              : revokeGroup(user, mapping.getUserGroup(), tenantId);
    }

    if (groupsChanged) {
      markingClearanceCacheManager.evictForUser(user.getId());
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

  private boolean grantGroup(User user, GroupMapping mapping, String tenantId) {
    if (tenantId != null) {
      attachTenant(tenantId, user);
    }
    List<Group> userGroups = user.getUnscopedGroups();
    if (userGroups.stream().anyMatch(g -> isMappedGroup(g, mapping.getUserGroup(), tenantId))) {
      return false;
    }
    Optional<Group> group = findOrCreateGroup(mapping, tenantId);
    group.ifPresent(userGroups::add);
    return group.isPresent();
  }

  /** The user left the group in the identity provider. */
  private static boolean revokeGroup(User user, String userGroup, String tenantId) {
    return user.getUnscopedGroups().removeIf(g -> isMappedGroup(g, userGroup, tenantId));
  }

  /** A mapping with a tenant ID targets a group of that tenant, otherwise a platform group. */
  private static boolean isMappedGroup(Group group, String name, String tenantId) {
    if (!group.getName().equals(name)) {
      return false;
    }
    return tenantId == null
        ? group.getTenant() == null
        : group.getTenant() != null && tenantId.equals(group.getTenant().getId());
  }

  private Optional<Group> findOrCreateGroup(GroupMapping mapping, String tenantId) {
    String userGroup = mapping.getUserGroup();
    Optional<Group> existing =
        tenantId == null
            ? groupRepository.findByNameAndTenantIsNull(userGroup)
            : groupRepository.findByNameAndTenantId(userGroup, tenantId);
    if (existing.isPresent()) {
      return existing;
    }
    if (!mapping.isAutoCreate()) {
      log.error(
          "Group '{}' not found in database and autoCreate is disabled for mapping '{}'",
          userGroup,
          mapping.getIdpGroup());
      return Optional.empty();
    }
    Group newGroup = new Group();
    newGroup.setName(userGroup);
    if (tenantId != null) {
      newGroup.setTenant(tenantRepository.getReferenceById(tenantId));
    }
    return Optional.of(groupRepository.save(newGroup));
  }

  private void attachTenant(String tenantId, User user) {
    boolean alreadyAttached = user.getTenants().stream().anyMatch(t -> t.getId().equals(tenantId));
    if (!alreadyAttached) {
      user.getTenants().add(tenantRepository.getReferenceById(tenantId));
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
      @Nonnull final AuthenticatedPrincipal user, @NotBlank final String registrationId) {
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
      @Nonnull final AuthenticatedPrincipal user, @NotBlank final String registrationId) {
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
      @Nonnull final AuthenticatedPrincipal user,
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
      @Nonnull final AuthenticatedPrincipal user, @NotBlank final String path) {
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
