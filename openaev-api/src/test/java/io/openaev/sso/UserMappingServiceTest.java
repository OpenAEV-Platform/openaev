package io.openaev.sso;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.Constants;
import io.openaev.service.UserMappingService;
import io.openaev.utils.fixtures.GroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.GroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
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

@Transactional
class UserMappingServiceTest extends IntegrationTest {

  private static final String GROUP_TENANT_ID = "group-tenant-id";

  @Autowired private GroupComposer groupComposer;
  @Autowired UserComposer userComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private UserMappingService userMappingService;
  @Autowired protected EntityManager entityManager;

  private static final String GROUPS_MANAGEMENT_JSON =
      """
      [
        {"idpGroup": "team-alpha", "userGroup": "Alpha", "autoCreate": true, "tenantId": "group-tenant-id"},
        {"idpGroup": "team-beta", "userGroup": "Beta", "autoCreate": true}
      ]
      """;

  @BeforeEach
  void setup() {
    groupComposer.reset();
  }

  @Test
  @DisplayName(
      "When the specific group already exists and the autocreate is false, add it to the user and"
          + " attach tenant")
  void whenTheSpecificGroupAlreadyExistsAndTheAutocreateIsFalse_addItToTheUser() {

    // -- ARRANGE ---
    Tenant tenant = TenantFixture.getTenant();
    tenant.setId(GROUP_TENANT_ID);
    tenantComposer.forTenant(tenant).persist();

    String groupsManagement = GROUPS_MANAGEMENT_JSON;
    Group specificGroup = GroupFixture.createGroupWithName("Alpha");
    specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("team-alpha");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(groupsManagement, user, roles);

    // -- ASSERT --
    assertTrue(user.getGroups().contains(specificGroup));
    assertThat(user.getTenants().stream().anyMatch(t -> t.getId().equals(GROUP_TENANT_ID)))
        .isTrue();
  }

  @Test
  @DisplayName(
      "When the specific group does not exist and the autocreate is true, create it and add it to"
          + " the user")
  void whenTheSpecificGroupDoesNotExistAndTheAutocreateIsTrue_createItAndAddItToTheUser() {

    // -- ARRANGE ---
    String groupsManagement = GROUPS_MANAGEMENT_JSON;
    User user = UserFixture.getUser();
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("team-beta");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(groupsManagement, user, roles);

    // -- ASSERT --
    Group userGroup = user.getGroups().get(0);
    assertTrue(userGroup.getName().equals("Beta"));
    assertThat(user.getTenants().isEmpty()).isTrue();
  }

  @Test
  @DisplayName("When the specific group does not exist and the autocreate is false, do nothing")
  void whenTheSpecificGroupDoesNotExistAndTheAutocreateIsFalse_doNothing() {

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
    assertThat(user.getGroups().size()).isEqualTo(0);
  }

  @Test
  @DisplayName("When group from idp and group from oaev do not match, do nothing")
  void whenGroupFromIdpAndRolesFromOaevDoNotMatch_doNothing() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer\",\"userGroup\": \"admin\",\"autoCreate\": \"false\"}]";
    Group specificGroup = GroupFixture.createGroupWithName("admin");
    specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup).persist();
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
    assertThat(user.getGroups().size()).isEqualTo(0);
  }

  @Test
  @DisplayName("When multiple config is set, act accordingly")
  void whenMultipleConfigIsSet_actAccordingly() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer\",\"userGroup\": \"admin1\",\"autoCreate\":"
            + " \"false\"},{\"idpGroup\": \"observer\",\"userGroup\": \"admin2\",\"autoCreate\":"
            + " \"true\"}]";
    Group specificGroup = GroupFixture.createGroupWithName("observer");
    specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
    specificGroup.setDescription("a description");
    specificGroup.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup).persist();
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
    assertThat(user.getGroups().size()).isEqualTo(1);
    assertThat(user.getGroups().getFirst().getName()).isEqualTo("admin2");
  }

  @Test
  @DisplayName("When removed from the idp group, remove from oaev group")
  void whenRemovedFromIdpGroup_propagateDeleteFromGroup() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer1\",\"userGroup\": \"observerOAEV1\",\"autoCreate\":"
            + " \"true\"},{\"idpGroup\": \"observer2\",\"userGroup\":"
            + " \"observerOAEV2\",\"autoCreate\": \"true\"}]";
    Group specificGroup1 = GroupFixture.createGroupWithName("observerOAEV1");
    specificGroup1.setId(Constants.PROCESS_STIX_GROUP_ID);
    specificGroup1.setDescription("a description");
    specificGroup1.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup1).persist();
    Group specificGroup2 = GroupFixture.createGroupWithName("observerOAEV2");
    specificGroup2.setId(Constants.PROCESS_STIX_ROLE_ID);
    specificGroup2.setDescription("a description");
    specificGroup2.setRoles(new ArrayList<>());
    groupComposer.forGroup(specificGroup2).persist();
    entityManager.flush();
    entityManager.clear();
    User user = UserFixture.getUser();
    user.getGroups().addAll(List.of(specificGroup1, specificGroup2));
    userComposer.forUser(user).persist();
    entityManager.flush();
    entityManager.clear();
    List<String> roles = List.of("observer1");

    // ---- ACT ----
    userMappingService.mapCurrentUserWithGroup(object, user, roles);

    // -- ASSERT --
    assertThat(user.getGroups().size()).isEqualTo(1);
    assertThat(user.getGroups().getFirst().getName()).isEqualTo("observerOAEV1");
  }

  @Nested
  class TestRolesAndGroupsExtraction {
    @Test
    @DisplayName("When oidc user, extract roles accordingly")
    void whenOidcUser_extractRoles() {
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
    void whenOidcUser_extractGroups() {
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
    void whenSamlUser_extractRoles() {
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
    void whenSamlUser_extractGroups() {
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
    void whenNotImplementedUser_throwException() {
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
