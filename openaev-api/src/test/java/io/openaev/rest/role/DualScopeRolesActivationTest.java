package io.openaev.rest.role;

import static io.openaev.api.platform.roles.PlatformRoleApi.PLATFORM_ROLES_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.api.platform.roles.PlatformRoleApi;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.RoleRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.service.account.CallerRightsInTenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code roles} table (entity {@code Role}) is active and dual-scope: a platform role ({@code
 * tenant_id IS NULL}) is read and written through {@link PlatformRoleApi}, a tenant role through
 * {@link TenantRoleApi}, on the tenant path and on the plain route with the {@code X-Tenant-Ids}
 * header. A tenant caller never reads or writes a role of another tenant, and every tenant reads
 * the platform roles. On the plain route the caller's rights in the default tenant are read in the
 * default tenant's scope for the permission check, while the request reads its rows in its own
 * scope.
 *
 * <p>The roles of the two tenants are seeded by raw JDBC, which sets no scope; every ground truth
 * is read by raw JDBC on the test's connection, which the statement inspector does not rewrite.
 * Each test issues one request: the scope of a transaction is set once.
 */
@CoversTenantIsolation("roles")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("roles active, dual-scope isolation")
class DualScopeRolesActivationTest extends ProductionTablesIntegrationTest {

  private static final String ROLES_ON_PATH = "/api/tenants/{tenantId}/roles";
  private static final String ROLE_ON_PATH = "/api/tenants/{tenantId}/roles/{roleId}";
  private static final String ROLES_ON_HEADER = TenantRoleApi.ROLE_URI;
  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String PLATFORM_ADMIN_ROLE_ID =
      AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.roleId();
  private static final Set<Capability> ROLE_CAPABILITIES =
      Set.of(
          Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES,
          Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES,
          Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES);

  @Autowired private MockMvc mvc;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoSpyBean private CallerRightsInTenant callerRightsInTenant;

  private String tenantA;
  private String tenantB;
  private String roleA;
  private String roleB;

  @BeforeEach
  void seedTwoTenantsWithOneRoleEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("dual-scope-roles-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("dual-scope-roles-b").getId();
    roleA = seedTenantRole(tenantA, "dual-scope-role-of-a");
    roleB = seedTenantRole(tenantB, "dual-scope-role-of-b");
  }

  private String seedTenantRole(String tenantId, String name) {
    String roleId = UUID.randomUUID().toString();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement role =
                      connection.prepareStatement(
                          "INSERT INTO roles (role_id, role_name, role_created_at,"
                              + " role_updated_at, tenant_id) VALUES (?, ?, now(), now(), ?)");
                  PreparedStatement capability =
                      connection.prepareStatement(
                          "INSERT INTO roles_capabilities (role_id, capability) VALUES (?, ?)")) {
                role.setString(1, roleId);
                role.setString(2, name);
                role.setString(3, tenantId);
                role.executeUpdate();
                capability.setString(1, roleId);
                capability.setString(2, Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES.name());
                capability.executeUpdate();
              }
            });
    return roleId;
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

  private String rawTenantOf(String roleId) {
    return rawString("SELECT tenant_id FROM roles WHERE role_id = ?", roleId);
  }

  private String rawNameOf(String roleId) {
    return rawString("SELECT role_name FROM roles WHERE role_id = ?", roleId);
  }

  private long rawCountByName(String name) {
    return Long.parseLong(rawString("SELECT count(*)::text FROM roles WHERE role_name = ?", name));
  }

  private RoleInput newTenantRole(String name) {
    return new RoleInput(name, null, Set.of(Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES));
  }

  private String body(ResultActions actions) throws Exception {
    return actions.andReturn().getResponse().getContentAsString();
  }

  @Nested
  @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES})
  @DisplayName("a caller without the admin flag, holding the role capabilities in A and not in B")
  class NonAdminCallerOfA {

    @BeforeEach
    void holdTheCapabilitiesInAOnly() {
      tenantHelper.grantCapabilitiesInTenant(tenantA, ROLE_CAPABILITIES);
      tenantHelper.detachCurrentUserFromTenant(tenantB);
    }

    @Test
    @DisplayName("given_aListUnderThePathOfA_should_returnTheTenantRolesOfAOnly")
    void given_aListUnderThePathOfA_should_returnTheTenantRolesOfAOnly() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(get(ROLES_ON_PATH, tenantA).accept(MediaType.APPLICATION_JSON))
                  .andExpect(status().isOk()));

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].role_id");
      assertThat(ids).contains(roleA).doesNotContain(roleB, PLATFORM_ADMIN_ROLE_ID);
      verify(callerRightsInTenant, never()).loadInAmbientTenant(any());
    }

    @Test
    @DisplayName("given_aReadOfTheRoleOfBUnderThePathOfA_should_answerNotFound")
    void given_aReadOfTheRoleOfBUnderThePathOfA_should_answerNotFound() throws Exception {
      // Act + Assert
      mvc.perform(get(ROLE_ON_PATH, tenantA, roleB)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given_anUpdateOfTheRoleOfBUnderThePathOfA_should_leaveItUntouched")
    void given_anUpdateOfTheRoleOfBUnderThePathOfA_should_leaveItUntouched() throws Exception {
      // Act
      mvc.perform(
              put(ROLE_ON_PATH, tenantA, roleB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newTenantRole("renamed-from-a")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals("dual-scope-role-of-b", rawNameOf(roleB));
    }

    @Test
    @DisplayName("given_aDeleteOfTheRoleOfBUnderThePathOfA_should_keepIt")
    void given_aDeleteOfTheRoleOfBUnderThePathOfA_should_keepIt() throws Exception {
      // Act
      mvc.perform(delete(ROLE_ON_PATH, tenantA, roleB).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(tenantB, rawTenantOf(roleB));
    }

    @Test
    @DisplayName("given_aCreateUnderThePathOfA_should_storeTheRoleInA")
    void given_aCreateUnderThePathOfA_should_storeTheRoleInA() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(
                      post(ROLES_ON_PATH, tenantA)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(newTenantRole("created-in-a-on-path")))
                          .with(csrf()))
                  .andExpect(status().isOk()));

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }

    @Test
    @DisplayName("given_aCreateUnderThePathOfB_should_refuseItAndStoreNothing")
    void given_aCreateUnderThePathOfB_should_refuseItAndStoreNothing() throws Exception {
      // Act
      mvc.perform(
              post(ROLES_ON_PATH, tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newTenantRole("created-in-b-from-a")))
                  .with(csrf()))
          .andExpect(status().isForbidden());

      // Assert
      assertEquals(0L, rawCountByName("created-in-b-from-a"));
    }

    /**
     * The plain route checks the rights of the caller in the default tenant as well as in the
     * tenants of the scope. The caller holds them in the default tenant through a role of the
     * default tenant, a row outside the scope of A that the check reads in the default tenant's
     * scope, and in A through its role of A.
     */
    @Test
    @DisplayName("given_aListSelectingAByHeader_should_returnTheTenantRolesOfAOnly")
    void given_aListSelectingAByHeader_should_returnTheTenantRolesOfAOnly() throws Exception {
      // Arrange
      givenNoAmbientTenant();
      AtomicReference<String> ambientTenantOfTheLoad = new AtomicReference<>();
      AtomicReference<Set<Capability>> loadedCapabilities = new AtomicReference<>();
      doAnswer(
              invocation -> {
                User caller = invocation.getArgument(0);
                ambientTenantOfTheLoad.set(caller.scopedTenantId());
                User loaded = (User) invocation.callRealMethod();
                loadedCapabilities.set(loaded.getCapabilities());
                return loaded;
              })
          .when(callerRightsInTenant)
          .loadInAmbientTenant(any());

      // Act
      String response =
          body(
              mvc.perform(
                      get(ROLES_ON_HEADER)
                          .header(TENANT_HEADER, tenantA)
                          .accept(MediaType.APPLICATION_JSON))
                  .andExpect(status().isOk()));

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].role_id");
      assertThat(ids).contains(roleA).doesNotContain(roleB, PLATFORM_ADMIN_ROLE_ID);
      verify(callerRightsInTenant).loadInAmbientTenant(any());
      assertEquals(Tenant.DEFAULT_TENANT_UUID, ambientTenantOfTheLoad.get());
      // The role of the caller's default-tenant group, unread in the scope of A, is read.
      assertThat(loadedCapabilities.get())
          .contains(Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES);
    }

    @Test
    @DisplayName("given_aCreateSelectingAByHeader_should_storeTheRoleInA")
    void given_aCreateSelectingAByHeader_should_storeTheRoleInA() throws Exception {
      // Arrange
      givenNoAmbientTenant();

      // Act
      String response =
          body(
              mvc.perform(
                      post(ROLES_ON_HEADER)
                          .header(TENANT_HEADER, tenantA)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(newTenantRole("created-in-a-by-header")))
                          .with(csrf()))
                  .andExpect(status().isOk()));

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }

    @Test
    @DisplayName("given_aPlatformRoleCreate_should_refuseItAndStoreNothing")
    void given_aPlatformRoleCreate_should_refuseItAndStoreNothing() throws Exception {
      // Act
      mvc.perform(
              post(PLATFORM_ROLES_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          new RoleInput(
                              "platform-role-from-a",
                              null,
                              Set.of(Capability.ACCESS_PLATFORM_SETTINGS))))
                  .with(csrf()))
          .andExpect(status().isForbidden());

      // Assert
      assertEquals(0L, rawCountByName("platform-role-from-a"));
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("a caller without the admin flag, holding the role capabilities in A only")
  class NonAdminCallerWithoutRightsInTheDefaultTenant {

    @BeforeEach
    void holdTheCapabilitiesInAOnly() {
      tenantHelper.grantCapabilitiesInTenant(tenantA, ROLE_CAPABILITIES);
      tenantHelper.detachCurrentUserFromTenant(tenantB);
      givenNoAmbientTenant();
    }

    /**
     * The plain route checks the rights of the caller in the default tenant as well as in the
     * tenants of the scope, and the caller holds none in the default tenant.
     */
    @Test
    @DisplayName("given_aListSelectingAByHeader_should_refuseIt")
    void given_aListSelectingAByHeader_should_refuseIt() throws Exception {
      // Act + Assert
      mvc.perform(get(ROLES_ON_HEADER).header(TENANT_HEADER, tenantA))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given_aListUnderThePathOfA_should_returnTheTenantRolesOfA")
    void given_aListUnderThePathOfA_should_returnTheTenantRolesOfA() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(get(ROLES_ON_PATH, tenantA).accept(MediaType.APPLICATION_JSON))
                  .andExpect(status().isOk()));

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].role_id");
      assertThat(ids).contains(roleA).doesNotContain(roleB);
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
    @DisplayName("given_aList_should_returnTheTenantRolesOfAOnly")
    void given_aList_should_returnTheTenantRolesOfAOnly() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(
                      get(ROLES_ON_HEADER)
                          .header(TENANT_HEADER, tenantA)
                          .accept(MediaType.APPLICATION_JSON))
                  .andExpect(status().isOk()));

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].role_id");
      assertThat(ids).contains(roleA).doesNotContain(roleB, PLATFORM_ADMIN_ROLE_ID);
      verify(callerRightsInTenant, never()).loadInAmbientTenant(any());
    }

    @Test
    @DisplayName("given_aReadOfTheRoleOfB_should_answerNotFound")
    void given_aReadOfTheRoleOfB_should_answerNotFound() throws Exception {
      // Act + Assert
      mvc.perform(get(ROLES_ON_HEADER + "/" + roleB).header(TENANT_HEADER, tenantA))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given_anUpdateOfTheRoleOfB_should_leaveItUntouched")
    void given_anUpdateOfTheRoleOfB_should_leaveItUntouched() throws Exception {
      // Act
      mvc.perform(
              put(ROLES_ON_HEADER + "/" + roleB)
                  .header(TENANT_HEADER, tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newTenantRole("renamed-by-header")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals("dual-scope-role-of-b", rawNameOf(roleB));
    }

    @Test
    @DisplayName("given_aDeleteOfTheRoleOfB_should_keepIt")
    void given_aDeleteOfTheRoleOfB_should_keepIt() throws Exception {
      // Act
      mvc.perform(delete(ROLES_ON_HEADER + "/" + roleB).header(TENANT_HEADER, tenantA).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(tenantB, rawTenantOf(roleB));
    }

    @Test
    @DisplayName("given_aCreate_should_storeTheRoleInA")
    void given_aCreate_should_storeTheRoleInA() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(
                      post(ROLES_ON_HEADER)
                          .header(TENANT_HEADER, tenantA)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(newTenantRole("created-in-a-by-header")))
                          .with(csrf()))
                  .andExpect(status().isOk()));

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }
  }

  @Nested
  @DisplayName("the statement inspector in the scope of A")
  class InspectorInTheScopeOfA {

    @Test
    @WithMockUser
    @DisplayName("given_theScopeOfA_should_notReadTheRoleOfB")
    void given_theScopeOfA_should_notReadTheRoleOfB() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act + Assert: the generic read by id, which the services filter after the fact
      assertThat(roleRepository.findById(roleB)).isEmpty();
      assertThat(roleRepository.findById(roleA)).isPresent();
      assertEquals(tenantB, rawTenantOf(roleB));
    }

    @Test
    @WithMockUser
    @DisplayName("given_theScopeOfA_should_readThePlatformAdminRole")
    void given_theScopeOfA_should_readThePlatformAdminRole() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act + Assert
      assertThat(roleRepository.findById(PLATFORM_ADMIN_ROLE_ID))
          .hasValueSatisfying(role -> assertNull(role.getTenant()));
    }
  }

  @Nested
  @WithMockUser(
      withCapabilities = {
        Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES,
        Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES
      })
  @DisplayName("a platform caller")
  class PlatformCaller {

    @Test
    @DisplayName("given_idsOfPlatformAndTenantRoles_should_findThePlatformRoleOnly")
    void given_idsOfPlatformAndTenantRoles_should_findThePlatformRoleOnly() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(
                      post(PLATFORM_ROLES_URI + "/find")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(List.of(PLATFORM_ADMIN_ROLE_ID, roleA, roleB)))
                          .with(csrf()))
                  .andExpect(status().isOk()));

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].role_id");
      assertThat(ids).containsExactly(PLATFORM_ADMIN_ROLE_ID);
    }

    @Test
    @DisplayName("given_aTenantRoleId_should_answerNotFound")
    void given_aTenantRoleId_should_answerNotFound() throws Exception {
      // Act + Assert
      mvc.perform(get(PLATFORM_ROLES_URI + "/" + roleA)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given_aCreate_should_storeAPlatformRole")
    void given_aCreate_should_storeAPlatformRole() throws Exception {
      // Act
      String response =
          body(
              mvc.perform(
                      post(PLATFORM_ROLES_URI)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              asJsonString(
                                  new RoleInput(
                                      "dual-scope-platform-role",
                                      null,
                                      Set.of(Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES))))
                          .with(csrf()))
                  .andExpect(status().isCreated()));

      // Assert
      String roleId = JsonPath.read(response, "$.role_id");
      assertEquals(1L, rawCountByName("dual-scope-platform-role"));
      assertNull(rawTenantOf(roleId));
    }
  }
}
