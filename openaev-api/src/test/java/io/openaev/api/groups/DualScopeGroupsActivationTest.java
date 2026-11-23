package io.openaev.api.groups;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code groups} table (entity {@code Group}) is active and dual-scope: a platform group
 * ({@code tenant_id IS NULL}) is read and written through {@link PlatformGroupApi}, a tenant group
 * through {@link TenantGroupApi}, on the tenant path and on the plain route with the {@code
 * X-Tenant-Ids} header. A tenant caller never reads or writes a group of another tenant, and every
 * tenant reads the platform groups.
 *
 * <p>The groups of the two tenants are seeded by raw JDBC, which sets no scope; every ground truth
 * is read by raw JDBC on the test's connection, which the statement inspector does not rewrite.
 * Each test issues one request: the scope of a transaction is set once.
 */
@CoversTenantIsolation("groups")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("groups active, dual-scope isolation")
class DualScopeGroupsActivationTest extends ProductionTablesIntegrationTest {

  private static final String GROUPS_ON_PATH = "/api/tenants/{tenantId}/groups";
  private static final String GROUP_ON_PATH = "/api/tenants/{tenantId}/groups/{groupId}";
  private static final String SEARCH_ON_PATH = "/api/tenants/{tenantId}/groups/search";
  private static final String INFORMATION_ON_PATH =
      "/api/tenants/{tenantId}/groups/{groupId}/information";
  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String PLATFORM_ADMIN_GROUP_ID =
      AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.groupId();
  private static final Set<Capability> GROUP_CAPABILITIES =
      Set.of(
          Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES,
          Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES,
          Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES);

  @Autowired private MockMvc mvc;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;
  private String groupA;
  private String groupB;
  private String platformGroup;

  @BeforeEach
  void seedTwoTenantsWithOneGroupEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("dual-scope-groups-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("dual-scope-groups-b").getId();
    groupA = seedGroup(tenantA, "dual-scope-group-of-a");
    groupB = seedGroup(tenantB, "dual-scope-group-of-b");
    platformGroup = seedGroup(null, "dual-scope-platform-group");
  }

  private String seedGroup(String tenantId, String name) {
    String groupId = UUID.randomUUID().toString();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement group =
                  connection.prepareStatement(
                      "INSERT INTO groups (group_id, group_name, group_default_user_assign,"
                          + " group_auto_new_marking, tenant_id) VALUES (?, ?, false, false, ?)")) {
                group.setString(1, groupId);
                group.setString(2, name);
                group.setString(3, tenantId);
                group.executeUpdate();
              }
            });
    return groupId;
  }

  /**
   * Production's thread-local when a request carries no path tenant: empty, so the default tenant.
   * The fixtures leave a tenant on the thread, which a MockMvc call would otherwise inherit.
   */
  private void givenNoAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertEquals(Tenant.DEFAULT_TENANT_UUID, TenantContext.getCurrentTenant());
  }

  private String rawString(String sql, String argument) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, argument);
                try (ResultSet rs = statement.executeQuery()) {
                  return rs.next() ? rs.getString(1) : null;
                }
              }
            });
  }

  private String rawTenantOf(String groupId) {
    return rawString("SELECT tenant_id FROM groups WHERE group_id = ?", groupId);
  }

  private String rawNameOf(String groupId) {
    return rawString("SELECT group_name FROM groups WHERE group_id = ?", groupId);
  }

  private long rawCountByName(String name) {
    return Long.parseLong(
        rawString("SELECT count(*)::text FROM groups WHERE group_name = ?", name));
  }

  private TenantGroupCreateInput newTenantGroup(String name) {
    TenantGroupCreateInput input = new TenantGroupCreateInput();
    input.setName(name);
    return input;
  }

  private String body(ResultActions actions) throws Exception {
    return actions.andReturn().getResponse().getContentAsString();
  }

  private List<String> searchedIds(ResultActions actions) throws Exception {
    return JsonPath.read(body(actions.andExpect(status().isOk())), "$.content[*].group_id");
  }

  @Nested
  @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES})
  @DisplayName("a caller without the admin flag, holding the group capabilities in A and not in B")
  class NonAdminCallerOfA {

    @BeforeEach
    void holdTheCapabilitiesInAOnly() {
      tenantHelper.grantCapabilitiesInTenant(tenantA, GROUP_CAPABILITIES);
      tenantHelper.detachCurrentUserFromTenant(tenantB);
    }

    @Test
    @DisplayName("given_aSearchUnderThePathOfA_should_returnTheTenantGroupsOfAOnly")
    void given_aSearchUnderThePathOfA_should_returnTheTenantGroupsOfAOnly() throws Exception {
      // Act
      List<String> ids =
          searchedIds(
              mvc.perform(
                  post(SEARCH_ON_PATH, tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"page\":0,\"size\":100}")
                      .with(csrf())));

      // Assert
      assertThat(ids).contains(groupA).doesNotContain(groupB, platformGroup);
    }

    @Test
    @DisplayName("given_aReadOfTheGroupOfAUnderThePathOfA_should_returnIt")
    void given_aReadOfTheGroupOfAUnderThePathOfA_should_returnIt() throws Exception {
      // Act
      String response =
          body(mvc.perform(get(GROUP_ON_PATH, tenantA, groupA)).andExpect(status().isOk()));

      // Assert
      assertEquals(groupA, JsonPath.read(response, "$.group_id"));
    }

    @Test
    @DisplayName("given_aReadOfTheGroupOfBUnderThePathOfA_should_answerNotFound")
    void given_aReadOfTheGroupOfBUnderThePathOfA_should_answerNotFound() throws Exception {
      // Act + Assert
      mvc.perform(get(GROUP_ON_PATH, tenantA, groupB)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given_anUpdateOfTheGroupOfBUnderThePathOfA_should_leaveItUntouched")
    void given_anUpdateOfTheGroupOfBUnderThePathOfA_should_leaveItUntouched() throws Exception {
      // Act
      mvc.perform(
              put(INFORMATION_ON_PATH, tenantA, groupB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newTenantGroup("renamed-from-a")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals("dual-scope-group-of-b", rawNameOf(groupB));
    }

    @Test
    @DisplayName("given_aDeleteOfTheGroupOfBUnderThePathOfA_should_keepIt")
    void given_aDeleteOfTheGroupOfBUnderThePathOfA_should_keepIt() throws Exception {
      // Act
      mvc.perform(delete(GROUP_ON_PATH, tenantA, groupB).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(tenantB, rawTenantOf(groupB));
    }

    @Test
    @DisplayName("given_aDeleteOfAPlatformGroupUnderThePathOfA_should_keepIt")
    void given_aDeleteOfAPlatformGroupUnderThePathOfA_should_keepIt() throws Exception {
      // Act
      mvc.perform(delete(GROUP_ON_PATH, tenantA, platformGroup).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertNull(rawTenantOf(platformGroup));
      assertEquals("dual-scope-platform-group", rawNameOf(platformGroup));
    }

    @Test
    @DisplayName("given_aCreateUnderThePathOfA_should_storeTheGroupInA")
    void given_aCreateUnderThePathOfA_should_storeTheGroupInA() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(
                      post(GROUPS_ON_PATH, tenantA)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(newTenantGroup("created-in-a-on-path")))
                          .with(csrf()))
                  .andExpect(status().isOk()));

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.group_id")));
    }

    @Test
    @DisplayName("given_aCreateUnderThePathOfB_should_refuseItAndStoreNothing")
    void given_aCreateUnderThePathOfB_should_refuseItAndStoreNothing() throws Exception {
      // Act
      mvc.perform(
              post(GROUPS_ON_PATH, tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newTenantGroup("created-in-b-from-a")))
                  .with(csrf()))
          .andExpect(status().isForbidden());

      // Assert
      assertEquals(0L, rawCountByName("created-in-b-from-a"));
    }

    @Test
    @DisplayName("given_aSearchSelectingAByHeader_should_returnTheTenantGroupsOfAOnly")
    void given_aSearchSelectingAByHeader_should_returnTheTenantGroupsOfAOnly() throws Exception {
      // Arrange
      givenNoAmbientTenant();

      // Act
      List<String> ids =
          searchedIds(
              mvc.perform(
                  post(GROUP_URI + "/search")
                      .header(TENANT_HEADER, tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"page\":0,\"size\":100}")
                      .with(csrf())));

      // Assert
      assertThat(ids).contains(groupA).doesNotContain(groupB, platformGroup);
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("an admin selecting A by header on the plain route")
  class AdminSelectingAByHeader {

    @BeforeEach
    void selectAByHeader() {
      givenNoAmbientTenant();
    }

    @Test
    @DisplayName("given_aSearch_should_returnTheTenantGroupsOfAOnly")
    void given_aSearch_should_returnTheTenantGroupsOfAOnly() throws Exception {
      // Act
      List<String> ids =
          searchedIds(
              mvc.perform(
                  post(GROUP_URI + "/search")
                      .header(TENANT_HEADER, tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"page\":0,\"size\":100}")
                      .with(csrf())));

      // Assert
      assertThat(ids).contains(groupA).doesNotContain(groupB, platformGroup);
    }

    @Test
    @DisplayName("given_aReadOfTheGroupOfA_should_returnIt")
    void given_aReadOfTheGroupOfA_should_returnIt() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(get(GROUP_URI + "/" + groupA).header(TENANT_HEADER, tenantA))
                  .andExpect(status().isOk()));

      // Assert
      assertEquals(groupA, JsonPath.read(response, "$.group_id"));
    }

    @Test
    @DisplayName("given_aReadOfTheGroupOfB_should_answerNotFound")
    void given_aReadOfTheGroupOfB_should_answerNotFound() throws Exception {
      // Act + Assert
      mvc.perform(get(GROUP_URI + "/" + groupB).header(TENANT_HEADER, tenantA))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given_anUpdateOfTheGroupOfA_should_renameIt")
    void given_anUpdateOfTheGroupOfA_should_renameIt() throws Exception {
      // Act
      mvc.perform(
              put(GROUP_URI + "/" + groupA + "/information")
                  .header(TENANT_HEADER, tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newTenantGroup("renamed-by-header")))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertEquals("renamed-by-header", rawNameOf(groupA));
    }

    @Test
    @DisplayName("given_anUpdateOfTheGroupOfB_should_leaveItUntouched")
    void given_anUpdateOfTheGroupOfB_should_leaveItUntouched() throws Exception {
      // Act
      mvc.perform(
              put(GROUP_URI + "/" + groupB + "/information")
                  .header(TENANT_HEADER, tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newTenantGroup("renamed-by-header")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals("dual-scope-group-of-b", rawNameOf(groupB));
    }

    @Test
    @DisplayName("given_aDeleteOfTheGroupOfB_should_keepIt")
    void given_aDeleteOfTheGroupOfB_should_keepIt() throws Exception {
      // Act
      mvc.perform(delete(GROUP_URI + "/" + groupB).header(TENANT_HEADER, tenantA).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(tenantB, rawTenantOf(groupB));
    }

    @Test
    @DisplayName("given_aCreate_should_storeTheGroupInA")
    void given_aCreate_should_storeTheGroupInA() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(
                      post(GROUP_URI)
                          .header(TENANT_HEADER, tenantA)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(newTenantGroup("created-in-a-by-header")))
                          .with(csrf()))
                  .andExpect(status().isOk()));

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.group_id")));
    }
  }

  @Nested
  @DisplayName("the statement inspector in the scope of A")
  class InspectorInTheScopeOfA {

    @Test
    @WithMockUser
    @DisplayName("given_theScopeOfA_should_notReadTheGroupOfB")
    void given_theScopeOfA_should_notReadTheGroupOfB() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act + Assert: the generic read by id, which the services filter after the fact
      assertThat(groupRepository.findById(groupB)).isEmpty();
      assertThat(groupRepository.findById(groupA)).isPresent();
      assertEquals(tenantB, rawTenantOf(groupB));
    }

    @Test
    @WithMockUser
    @DisplayName("given_theScopeOfA_should_readAPlatformGroup")
    void given_theScopeOfA_should_readAPlatformGroup() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act + Assert
      assertThat(groupRepository.findById(platformGroup))
          .hasValueSatisfying(group -> assertNull(group.getTenant()));
      assertThat(groupRepository.findById(PLATFORM_ADMIN_GROUP_ID)).isPresent();
    }

    @Test
    @WithMockUser
    @DisplayName("given_theScopeOfA_should_refuseToWriteAPlatformGroupOutsideAPlatformWrite")
    void given_theScopeOfA_should_refuseToWriteAPlatformGroupOutsideAPlatformWrite() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
      var group = groupRepository.findById(platformGroup).orElseThrow();
      group.setDescription("rewritten from the scope of A");

      // Act + Assert
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> groupRepository.saveAndFlush(group))
          .rootCause()
          .hasMessageContaining(
              "write of a platform row refused: the transaction is not platform-write");
    }
  }
}
