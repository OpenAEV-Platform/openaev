package io.openaev.sso;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.service.UserMappingService;
import io.openaev.service.utils.ReadPropertiesHelper;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class UserMappingServiceTest extends IntegrationTest {

  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired UserComposer userComposer;
  @Autowired private UserMappingService userMappingService;
  @Autowired private ReadPropertiesHelper readPropertiesHelper;
  @Autowired protected EntityManager entityManager;

  private static final String TEST_REGISTRATION_ID = "test";
  private Environment originalEnvironment;

  @BeforeEach
  public void setup() {
    tenantGroupComposer.reset();
    originalEnvironment = (Environment) ReflectionTestUtils.getField(readPropertiesHelper, "env");
  }

  @AfterEach
  public void tearDown() {
    ReflectionTestUtils.setField(readPropertiesHelper, "env", originalEnvironment);
  }

  private String groupId() {
    return UUID.randomUUID().toString();
  }

  private String secondaryGroupId() {
    return UUID.randomUUID().toString();
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
      userMappingService.mapCurrentUserWithGroup(null, TEST_REGISTRATION_ID, user, roles);
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
      userMappingService.mapCurrentUserWithGroup("", TEST_REGISTRATION_ID, user, roles);
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
      userMappingService.mapCurrentUserWithGroup("   ", TEST_REGISTRATION_ID, user, roles);
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
    }
  }

  @Test
  @DisplayName(
      "When the specific group already exists and the autocreate is false, add it to the user")
  public void whenTheSpecificGroupAlreadyExistsAndTheAutocreateIsFalse_addItToTheUser() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer\",\"userGroup\": \"observerUserGroup\",\"autoCreate\": \"false\"}]";
    Group specificGroup = TenantGroupFixture.getGroup("observerUserGroup");
    specificGroup.setId(groupId());
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
    userMappingService.mapCurrentUserWithGroup(object, TEST_REGISTRATION_ID, user, roles);

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
    userMappingService.mapCurrentUserWithGroup(object, TEST_REGISTRATION_ID, user, roles);

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
    userMappingService.mapCurrentUserWithGroup(object, TEST_REGISTRATION_ID, user, roles);

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
    specificGroup.setId(groupId());
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
    userMappingService.mapCurrentUserWithGroup(object, TEST_REGISTRATION_ID, user, roles);

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
    specificGroup.setId(groupId());
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
    userMappingService.mapCurrentUserWithGroup(object, TEST_REGISTRATION_ID, user, roles);

    // -- ASSERT --
    assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
    assertThat(user.getUnscopedGroups().getFirst().getName()).isEqualTo("admin2");
  }

  @Test
  @DisplayName("When removed from the idp group, remove from oaev group")
  public void whenRemovedFromIdpGroup_propagateDeleteFromGroup() {

    // -- ARRANGE ---
    String object =
        "[{\"idpGroup\": \"observer1\",\"userGroup\": \"observerOAEV1\",\"autoCreate\": \"true\"},{\"idpGroup\": \"observer2\",\"userGroup\": \"observerOAEV2\",\"autoCreate\": \"true\"}]";
    Group specificGroup1 = TenantGroupFixture.getGroup("observerOAEV1");
    specificGroup1.setId(groupId());
    specificGroup1.setDescription("a description");
    specificGroup1.setRoles(new ArrayList<>());
    tenantGroupComposer.forGroup(specificGroup1).persist();
    Group specificGroup2 = TenantGroupFixture.getGroup("observerOAEV2");
    specificGroup2.setId(secondaryGroupId());
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
    userMappingService.mapCurrentUserWithGroup(object, TEST_REGISTRATION_ID, user, roles);

    // -- ASSERT --
    assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
    assertThat(user.getUnscopedGroups().getFirst().getName()).isEqualTo("observerOAEV1");
  }

  @Nested
  @DisplayName("When multiple idpGroups map to the same userGroup")
  class MultipleIdpGroupsToSameUserGroup {

    @Test
    @DisplayName(
        "Given multiple mappings to same group and one matches token, should add user to group")
    void given_multipleMappingsToSameGroupAndOneMatches_should_addUserToGroup() {
      // -- ARRANGE --
      String object =
          "[{\"idpGroup\": \"Filigran\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true},"
              + "{\"idpGroup\": \"Admin\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true},"
              + "{\"idpGroup\": \"Manager\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true}]";
      User user = UserFixture.getUser();
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();
      // Token contains only "Filigran" — not "Admin" or "Manager"
      List<String> groupsFromToken = List.of("Filigran");

      // -- ACT --
      userMappingService.mapCurrentUserWithGroup(
          object, TEST_REGISTRATION_ID, user, groupsFromToken);

      // -- ASSERT --
      assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
      assertThat(user.getUnscopedGroups().getFirst().getName()).isEqualTo("GROUP_A");
    }

    @Test
    @DisplayName(
        "Given multiple mappings to same group and none matches token, should not add user to group")
    void given_multipleMappingsToSameGroupAndNoneMatches_should_notAddUserToGroup() {
      // -- ARRANGE --
      String object =
          "[{\"idpGroup\": \"Filigran\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true},"
              + "{\"idpGroup\": \"Admin\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true}]";
      User user = UserFixture.getUser();
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();
      // Token contains "SomethingElse" — neither "Filigran" nor "Admin"
      List<String> groupsFromToken = List.of("SomethingElse");

      // -- ACT --
      userMappingService.mapCurrentUserWithGroup(
          object, TEST_REGISTRATION_ID, user, groupsFromToken);

      // -- ASSERT --
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
    }

    @Test
    @DisplayName(
        "Given user already in group and one mapping still matches token, should not remove user from group")
    void given_userInGroupAndOneMappingStillMatches_should_notRemoveUser() {
      // -- ARRANGE --
      String object =
          "[{\"idpGroup\": \"Filigran\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true},"
              + "{\"idpGroup\": \"Admin\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true},"
              + "{\"idpGroup\": \"Manager\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true}]";
      Group groupA = TenantGroupFixture.getGroup("GROUP_A");
      groupA.setId(groupId());
      groupA.setRoles(new ArrayList<>());
      tenantGroupComposer.forGroup(groupA).persist();
      entityManager.flush();
      entityManager.clear();
      User user = UserFixture.getUser();
      user.getUnscopedGroups().add(groupA);
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();
      // User is only in "Filigran" — removed from "Admin" and "Manager" in IdP
      List<String> groupsFromToken = List.of("Filigran");

      // -- ACT --
      userMappingService.mapCurrentUserWithGroup(
          object, TEST_REGISTRATION_ID, user, groupsFromToken);

      // -- ASSERT --
      assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
      assertThat(user.getUnscopedGroups().getFirst().getName()).isEqualTo("GROUP_A");
    }

    @Test
    @DisplayName(
        "Given user in group but no mapping matches token anymore, should remove user from group")
    void given_userInGroupButNoMappingMatches_should_removeUserFromGroup() {
      // -- ARRANGE --
      String object =
          "[{\"idpGroup\": \"Filigran\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true},"
              + "{\"idpGroup\": \"Admin\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true}]";
      Group groupA = TenantGroupFixture.getGroup("GROUP_A");
      groupA.setId(groupId());
      groupA.setRoles(new ArrayList<>());
      tenantGroupComposer.forGroup(groupA).persist();
      entityManager.flush();
      entityManager.clear();
      User user = UserFixture.getUser();
      user.getUnscopedGroups().add(groupA);
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();
      // User is no longer in "Filigran" or "Admin" — completely removed from IdP groups
      List<String> groupsFromToken = List.of("SomethingElse");

      // -- ACT --
      userMappingService.mapCurrentUserWithGroup(
          object, TEST_REGISTRATION_ID, user, groupsFromToken);

      // -- ASSERT --
      assertThat(user.getUnscopedGroups().size()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("When mappings target different userGroups")
  class DifferentUserGroups {

    @Test
    @DisplayName(
        "Given mappings to GROUP_A and GROUP_B, only matching one removes the other correctly")
    void given_mappingsToGroupAAndGroupB_should_removeUnmatchedGroupOnly() {
      // -- ARRANGE --
      String object =
          "[{\"idpGroup\": \"Filigran\",\"userGroup\": \"GROUP_A\",\"autoCreate\": true},"
              + "{\"idpGroup\": \"Engineers\",\"userGroup\": \"GROUP_B\",\"autoCreate\": true}]";
      Group groupA = TenantGroupFixture.getGroup("GROUP_A");
      groupA.setId(groupId());
      groupA.setRoles(new ArrayList<>());
      tenantGroupComposer.forGroup(groupA).persist();
      Group groupB = TenantGroupFixture.getGroup("GROUP_B");
      groupB.setId(secondaryGroupId());
      groupB.setRoles(new ArrayList<>());
      tenantGroupComposer.forGroup(groupB).persist();
      entityManager.flush();
      entityManager.clear();
      User user = UserFixture.getUser();
      user.getUnscopedGroups().addAll(List.of(groupA, groupB));
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();
      // User is in "Filigran" but NOT in "Engineers" anymore
      List<String> groupsFromToken = List.of("Filigran");

      // -- ACT --
      userMappingService.mapCurrentUserWithGroup(
          object, TEST_REGISTRATION_ID, user, groupsFromToken);

      // -- ASSERT -- GROUP_A stays, GROUP_B is removed
      assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
      assertThat(user.getUnscopedGroups().getFirst().getName()).isEqualTo("GROUP_A");
    }
  }

  @Nested
  @DisplayName("When provider user scope is configured")
  class ProviderUserScope {

    @Test
    @DisplayName(
        "Given no user_scope and no tenant_id, should auto-create tenant-scoped group in default tenant")
    void given_noUserScopeAndNoTenantId_should_createTenantScopedGroupInDefaultTenant() {
      // -- ARRANGE --
      mockProviderConfiguration(TEST_REGISTRATION_ID, "", null);
      String object =
          "[{\"idpGroup\": \"observer\",\"userGroup\": \"GROUP_SCOPE_DEFAULT\",\"autoCreate\": true}]";
      User user = UserFixture.getUser();
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();

      // -- ACT --
      userMappingService.mapCurrentUserWithGroup(
          object, TEST_REGISTRATION_ID, user, List.of("observer"));

      // -- ASSERT --
      assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
      Group mappedGroup = user.getUnscopedGroups().getFirst();
      assertThat(mappedGroup.getName()).isEqualTo("GROUP_SCOPE_DEFAULT");
      assertThat(mappedGroup.getTenant()).isNotNull();
      assertThat(mappedGroup.getTenant().getId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      assertThat(
              user.getTenants().stream()
                  .anyMatch(tenant -> Tenant.DEFAULT_TENANT_UUID.equals(tenant.getId())))
          .isTrue();
    }

    @Test
    @DisplayName("Given platform user_scope, should auto-create a platform-scoped group")
    void given_platformUserScope_should_createPlatformScopedGroup() {
      // -- ARRANGE --
      mockProviderConfiguration(TEST_REGISTRATION_ID, Tenant.DEFAULT_TENANT_UUID, "platform");
      String object =
          "[{\"idpGroup\": \"observer\",\"userGroup\": \"GROUP_SCOPE_PLATFORM\",\"autoCreate\": true}]";
      User user = UserFixture.getUser();
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();

      // -- ACT --
      userMappingService.mapCurrentUserWithGroup(
          object, TEST_REGISTRATION_ID, user, List.of("observer"));

      // -- ASSERT --
      assertThat(user.getUnscopedGroups().size()).isEqualTo(1);
      Group mappedGroup = user.getUnscopedGroups().getFirst();
      assertThat(mappedGroup.getName()).isEqualTo("GROUP_SCOPE_PLATFORM");
      assertThat(mappedGroup.getTenant()).isNull();
    }

    @Test
    @DisplayName(
        "Given platform and tenant user_scope, should auto-create both platform and tenant groups")
    void given_platformAndTenantUserScope_should_createBothScopedGroups() {
      // -- ARRANGE --
      mockProviderConfiguration(TEST_REGISTRATION_ID, "", "{platform,tenant}");
      String object =
          "[{\"idpGroup\": \"observer\",\"userGroup\": \"GROUP_SCOPE_BOTH\",\"autoCreate\": true}]";
      User user = UserFixture.getUser();
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();

      // -- ACT --
      userMappingService.mapCurrentUserWithGroup(
          object, TEST_REGISTRATION_ID, user, List.of("observer"));

      // -- ASSERT --
      assertThat(user.getUnscopedGroups().size()).isEqualTo(2);
      long platformScopedGroups =
          user.getUnscopedGroups().stream().filter(group -> group.getTenant() == null).count();
      long tenantScopedGroups =
          user.getUnscopedGroups().stream()
              .filter(
                  group ->
                      group.getTenant() != null
                          && Tenant.DEFAULT_TENANT_UUID.equals(group.getTenant().getId()))
              .count();
      assertThat(platformScopedGroups).isEqualTo(1L);
      assertThat(tenantScopedGroups).isEqualTo(1L);
    }
  }

  private void mockProviderConfiguration(String registrationId, String tenantId, String userScope) {
    Environment env = Mockito.mock(Environment.class);
    when(env.getProperty(Mockito.anyString(), Mockito.eq(String.class), Mockito.anyString()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    if (tenantId != null) {
      when(env.getProperty("openaev.provider." + registrationId + ".tenant_id", String.class, ""))
          .thenReturn(tenantId);
    }
    if (userScope != null) {
      when(env.getProperty(
              "openaev.provider." + registrationId + ".user_scope", String.class, "{tenant}"))
          .thenReturn(userScope);
    }
    ReflectionTestUtils.setField(readPropertiesHelper, "env", env);
  }
}
