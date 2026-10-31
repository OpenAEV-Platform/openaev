package io.openaev.sso;

import static io.openaev.service.account.ReservedServiceAccount.OPENCTI_CONNECTOR;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.service.UserMappingService;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class UserMappingServiceTest extends IntegrationTest {

  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired UserComposer userComposer;
  @Autowired private UserMappingService userMappingService;
  @Autowired protected EntityManager entityManager;

  @BeforeEach
  public void setup() {
    tenantGroupComposer.reset();
  }

  private String tenantScopedId(String id) {
    return UUID.nameUUIDFromBytes(
            (UUID.fromString(id) + ":" + TenantContext.getCurrentTenant()).getBytes())
        .toString();
  }

  private String tenantScopedGroupId() {
    return tenantScopedId(OPENCTI_CONNECTOR.access().groupId());
  }

  private String tenantScopedRoleId() {
    return tenantScopedId(OPENCTI_CONNECTOR.access().roleId());
  }

  @Nested
  @DisplayName("When groups_management property is not configured")
  class EmptyGroupMappings {

    @Test
    @DisplayName("Given null or empty mapping, should not throw and not modify user groups")
    void given_nullOrEmptyMapping_should_notThrowAndNotModifyUserGroups() {
      // -- ARRANGE --
      User user = UserFixture.getUser();
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();
      List<String> roles = List.of("observer");

      // -- ACT & ASSERT --
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
      userMappingService.mapCurrentUserWithGroup(null, user, roles);
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
      userMappingService.mapCurrentUserWithGroup("", user, roles);
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
      userMappingService.mapCurrentUserWithGroup("   ", user, roles);
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
    }
  }

  @Test
  @DisplayName(
      "When the specific group already exists and the autocreate is false, add it to the user")
  public void whenTheSpecificGroupAlreadyExistsAndTheAutocreateIsFalse_addItToTheUser() {

    // -- ARRANGE ---
    String object =
        "[{\"tenantId\": \"%s\",\"idpGroup\": \"observer\",\"userGroup\": \"observerUserGroup\",\"autoCreate\": \"false\"}]"
            .formatted(TenantContext.getCurrentTenant());
    Group specificGroup = TenantGroupFixture.getGroup("observerUserGroup");
    specificGroup.setId(tenantScopedGroupId());
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    tenantGroupComposer.forGroup(specificGroup).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    // -- ASSERT --
    assertTrue(user.getUnscopedGroups().contains(specificGroup));
  }

  @Test
  @DisplayName(
      "When the specific group does not exist and the autocreate is true, create it and add it to the user")
  public void whenTheSpecificGroupDoesNotExistAndTheAutocreateIsTrue_createItAndAddItToTheUser() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer\",\"userGroup\": \"admin\",\"autoCreate\": \"true\"}]";
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    // -- ASSERT --
    Group userGroup = user.getUnscopedGroups().get(0);
    assertTrue(userGroup.getName().equals("admin"));
  }

  @Test
  @DisplayName("When the specific group does not exist and the autocreate is false, do nothing")
  public void whenTheSpecificGroupDoesNotExistAndTheAutocreateIsFalse_doNothing() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer\",\"userGroup\": \"admin\",\"autoCreate\": \"false\"}]";
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    // -- ASSERT --
    assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
  }

  @Test
  @DisplayName("When group from idp and group from oaev do not match, do nothing")
  public void whenGroupFromIdpAndRolesFromOaevDoNotMatch_doNothing() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer\",\"userGroup\": \"admin\",\"autoCreate\": \"false\"}]";
    Group specificGroup = TenantGroupFixture.getGroup("admin");
    specificGroup.setId(tenantScopedGroupId());
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    tenantGroupComposer.forGroup(specificGroup).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("admin");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    // -- ASSERT --
    assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
  }

  @Test
  @DisplayName("When multiple config is set, act accordingly")
  public void whenMultipleConfigIsSet_actAccordingly() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer\",\"userGroup\": \"admin1\",\"autoCreate\": \"false\"},{\"idpGroup\": \"observer\",\"userGroup\": \"admin2\",\"autoCreate\": \"true\"}]";
    Group specificGroup = TenantGroupFixture.getGroup("observer");
    specificGroup.setId(tenantScopedGroupId());
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    tenantGroupComposer.forGroup(specificGroup).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    // -- ASSERT --
    assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
    assertThat(user.getUnscopedGroups().getFirst().getName()).isEqualTo("admin2");
  }

  @Test
  @DisplayName("When removed from the idp group, remove from oaev group")
  public void whenRemovedFromIdpGroup_propagateDeleteFromGroup() {

    // -- ARRANGE ---
    String object =
        "[{\"tenantId\": \"%1$s\",\"idpGroup\": \"observer1\",\"userGroup\": \"observerOAEV1\",\"autoCreate\": \"true\"},{\"tenantId\": \"%1$s\",\"idpGroup\": \"observer2\",\"userGroup\": \"observerOAEV2\",\"autoCreate\": \"true\"}]"
            .formatted(TenantContext.getCurrentTenant());
    Group specificGroup1 = TenantGroupFixture.getGroup("observerOAEV1");
    specificGroup1.setId(tenantScopedGroupId());
    specificGroup1.setDescription("a description");
    specificGroup1.setRoles(new ArrayList<>());
    tenantGroupComposer.forGroup(specificGroup1).persist();
    Group specificGroup2 = TenantGroupFixture.getGroup("observerOAEV2");
    specificGroup2.setId(tenantScopedRoleId());
    specificGroup2.setDescription("a description");
    specificGroup2.setRoles(new ArrayList<>());
    tenantGroupComposer.forGroup(specificGroup2).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    user.getUnscopedGroups().addAll(List.of(specificGroup1, specificGroup2));
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer1");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    // -- ASSERT --
    assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
    assertThat(user.getUnscopedGroups().getFirst().getName()).isEqualTo("observerOAEV1");
  }

  @Test
  @DisplayName("With a tenant ID the group is a tenant group, without it a platform group")
  public void whenTenantIdIsSet_useTenantGroup_otherwisePlatformGroup() {

    // -- ARRANGE ---
    String tenantId = TenantContext.getCurrentTenant();
    String object =
        "[{\"idpGroup\": \"observer\",\"userGroup\": \"SOC\",\"autoCreate\": \"true\"},{\"tenantId\": \"%s\",\"idpGroup\": \"observer\",\"userGroup\": \"SOC\",\"autoCreate\": \"true\"}]"
            .formatted(tenantId);
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, List.of("observer"));

    // -- ASSERT --
    List<Group> groups = user.getUnscopedGroups();
    assertThat(groups.size()).isEqualTo(2);
    assertTrue(groups.stream().anyMatch(group -> group.getTenant() == null));
    assertTrue(
        groups.stream()
            .anyMatch(
                group -> group.getTenant() != null && tenantId.equals(group.getTenant().getId())));
    assertTrue(user.getTenants().stream().anyMatch(t -> t.getId().equals(tenantId)));
  }

  @Nested
  class TestRolesAndGroupsExtraction {
    @Test
    @DisplayName("When oidc user, extract roles accordingly")
    public void whenOidcUser_extractRoles() {
      // -- ARRANGE ---
      Environment env = Mockito.mock(Environment.class);

      ReflectionTestUtils.setField(userMappingService, "env", env);
      when(env.getProperty("openaev.provider.oidc.roles_path", List.class, new ArrayList<String>()))
          .thenReturn(List.of("roles"));

      String role = "Administrator";

      OAuth2User user =
          new OAuth2User() {
            @Override
            public Map<String, Object> getAttributes() {
              return Map.of("roles", List.of(role));
            }

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
              return List.of();
            }

            @Override
            public String getName() {
              return "";
            }
          };

      // ---- ACT ----
      List<String> roles = userMappingService.extractRolesFromUser(user, "oidc");

      // -- ASSERT --
      assertThat(roles).isEqualTo(List.of(role));
    }

    @Test
    @DisplayName("When oidc user, extract groups accordingly")
    public void whenOidcUser_extractGroups() {
      // -- ARRANGE ---
      Environment env = Mockito.mock(Environment.class);

      ReflectionTestUtils.setField(userMappingService, "env", env);
      when(env.getProperty(
              "openaev.provider.oidc.groups_path", List.class, new ArrayList<String>()))
          .thenReturn(List.of("groups"));

      String group = "Filigran";

      OAuth2User user =
          new OAuth2User() {
            @Override
            public Map<String, Object> getAttributes() {
              return Map.of("groups", List.of(group));
            }

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
              return List.of();
            }

            @Override
            public String getName() {
              return "";
            }
          };

      // ---- ACT ----
      List<String> roles = userMappingService.extractGroupsFromUser(user, "oidc");

      // -- ASSERT --
      assertThat(roles).isEqualTo(List.of(group));
    }

    @Test
    @DisplayName("When saml user, extract roles accordingly")
    public void whenSamlUser_extractRoles() {
      // -- ARRANGE ---
      Environment env = Mockito.mock(Environment.class);

      ReflectionTestUtils.setField(userMappingService, "env", env);
      when(env.getProperty("openaev.provider.saml.roles_path", List.class, new ArrayList<String>()))
          .thenReturn(List.of("roles"));

      String role = "Administrator";

      Saml2AuthenticatedPrincipal user =
          new Saml2AuthenticatedPrincipal() {
            @Override
            public String getName() {
              return "";
            }

            @Override
            public Map<String, List<Object>> getAttributes() {
              return Map.of("roles", List.of(role));
            }
          };

      // ---- ACT ----
      List<String> roles = userMappingService.extractRolesFromUser(user, "saml");

      // -- ASSERT --
      assertThat(roles).isEqualTo(List.of(role));
    }

    @Test
    @DisplayName("When saml user, extract groups accordingly")
    public void whenSamlUser_extractGroups() {
      // -- ARRANGE ---
      Environment env = Mockito.mock(Environment.class);

      ReflectionTestUtils.setField(userMappingService, "env", env);
      when(env.getProperty(
              "openaev.provider.saml.groups_path", List.class, new ArrayList<String>()))
          .thenReturn(List.of("groups"));

      String group = "Filigran";

      Saml2AuthenticatedPrincipal user =
          new Saml2AuthenticatedPrincipal() {
            @Override
            public String getName() {
              return "";
            }

            @Override
            public Map<String, List<Object>> getAttributes() {
              return Map.of("groups", List.of(group));
            }
          };

      // ---- ACT ----
      List<String> roles = userMappingService.extractGroupsFromUser(user, "saml");

      // -- ASSERT --
      assertThat(roles).isEqualTo(List.of(group));
    }

    @Test
    @DisplayName("When not implemented user, throw exception")
    public void whenNotImplementedUser_throwException() {
      // -- ARRANGE ---
      Environment env = Mockito.mock(Environment.class);

      ReflectionTestUtils.setField(userMappingService, "env", env);
      when(env.getProperty(
              "openaev.provider.oidc.groups_path", List.class, new ArrayList<String>()))
          .thenReturn(List.of("groups"));

      AuthenticatedPrincipal user =
          new AuthenticatedPrincipal() {
            @Override
            public String getName() {
              return "";
            }
          };

      // ---- ACT ----

      // -- ASSERT --
      assertThrows(
          NotImplementedException.class,
          () -> userMappingService.extractGroupsFromUser(user, "oidc"));
    }
  }
}
