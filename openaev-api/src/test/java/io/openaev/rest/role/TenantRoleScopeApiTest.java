package io.openaev.rest.role;

import static io.openaev.rest.exception.PrivilegeGrantException.UNHELD_CAPABILITIES;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant of a role request comes from the request scope, on both routes: the tenant path and
 * the {@code X-Tenant-Ids} header on the plain route.
 *
 * <p>{@code TenantContext} falls back to the default tenant whenever the route carries no {@code
 * {tenantId}}, so on the header route the thread-local and the request scope name different
 * tenants. Every assertion below reads {@code tenant_id} through the test connection's raw JDBC, so
 * it measures what was stored and not what a scope lets the test see.
 *
 * <p>Each test issues one request: the scope of a transaction is set once.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Role requests take their tenant from the request scope")
class TenantRoleScopeApiTest extends IntegrationTest {

  private static final String ROLES_ON_PATH = "/api/tenants/{tenantId}/roles";
  private static final String ROLE_ON_PATH = "/api/tenants/{tenantId}/roles/{roleId}";
  private static final String ROLES_ON_HEADER = TenantRoleApi.ROLE_URI;
  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @PersistenceContext private EntityManager entityManager;

  private String tenantA;
  private String tenantB;
  private String roleA;
  private String roleB;

  @BeforeEach
  void seedTwoTenantsWithOneRoleEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("role-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("role-scope-b").getId();
    roleA = seedRole(tenantA, "role-of-a");
    roleB = seedRole(tenantB, "role-of-b");
  }

  private String seedRole(String tenantId, String name) {
    Role role =
        TenantRoleFixture.getRole(name, Set.of(Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES));
    role.setTenant(new Tenant(tenantId));
    String id = roleRepository.save(role).getId();
    entityManager.flush();
    return id;
  }

  /**
   * Production's thread-local when a request carries no path tenant: empty, so the default tenant.
   * The fixtures leave a tenant on the thread, which a MockMvc call would otherwise inherit.
   */
  private void givenNoAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertEquals(Tenant.DEFAULT_TENANT_UUID, TenantContext.getCurrentTenant());
  }

  private RoleInput newRole(String name) {
    return new RoleInput(name, null, Set.of(Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES));
  }

  private String createOnHeader(String tenantHeader, String name, int expectedStatus)
      throws Exception {
    givenNoAmbientTenant();
    var request =
        post(ROLES_ON_HEADER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(newRole(name)))
            .with(csrf());
    if (tenantHeader != null) {
      request.header(TENANT_HEADER, tenantHeader);
    }
    return mvc.perform(request)
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private List<String> roleIdsOfListOnHeader(String tenantHeader) throws Exception {
    givenNoAmbientTenant();
    var request = get(ROLES_ON_HEADER).accept(MediaType.APPLICATION_JSON).with(csrf());
    if (tenantHeader != null) {
      request.header(TENANT_HEADER, tenantHeader);
    }
    String response =
        mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$[*].role_id");
  }

  private String rawTenantOf(String roleId) {
    return rawString("SELECT tenant_id FROM roles WHERE role_id = ?", roleId);
  }

  private String rawNameOf(String roleId) {
    return rawString("SELECT role_name FROM roles WHERE role_id = ?", roleId);
  }

  private long rawCountByName(String name) {
    String count = rawString("SELECT count(*)::text FROM roles WHERE role_name = ?", name);
    return Long.parseLong(count);
  }

  private Set<String> rawCapabilitiesOf(String roleId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT capability FROM roles_capabilities WHERE role_id = ?")) {
                statement.setString(1, roleId);
                try (ResultSet rs = statement.executeQuery()) {
                  Set<String> capabilities = new HashSet<>();
                  while (rs.next()) {
                    capabilities.add(rs.getString(1));
                  }
                  return capabilities;
                }
              }
            });
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

  @Nested
  @DisplayName("a caller of the default tenant, A and B")
  class DefaultAndTwoTenants {

    private String roleDefault;

    @BeforeEach
    void joinTheDefaultTenant() {
      tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
      roleDefault = seedRole(Tenant.DEFAULT_TENANT_UUID, "role-of-default");
    }

    @Test
    @DisplayName("given a create under tenant A's path, should store the role in tenant A")
    void given_createUnderPathOfA_should_storeTheRoleInA() throws Exception {
      // Act
      String response =
          mvc.perform(
                  post(ROLES_ON_PATH, tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(newRole("created-on-path")))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }

    @Test
    @DisplayName(
        "given a create selecting tenant A by header, should store the role in A and not in the"
            + " thread-local default")
    void given_createSelectingAByHeader_should_storeTheRoleInA() throws Exception {
      // Act
      String response = createOnHeader(tenantA, "created-on-header", 200);

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }

    @Test
    @DisplayName(
        "given a create with no selector, should store the role in the default tenant the caller"
            + " belongs to")
    void given_createWithNoSelector_should_storeTheRoleInTheDefaultTenant() throws Exception {
      // Act
      String response = createOnHeader(null, "created-without-selector", 200);

      // Assert
      assertEquals(Tenant.DEFAULT_TENANT_UUID, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }

    @Test
    @DisplayName("given a create selecting two tenants, should refuse it and store nothing")
    void given_createSelectingTwoTenants_should_refuseAndStoreNothing() throws Exception {
      // Act
      createOnHeader(Tenant.DEFAULT_TENANT_UUID + "," + tenantA, "created-on-two", 400);

      // Assert
      assertEquals(0L, rawCountByName("created-on-two"));
    }

    @Test
    @DisplayName("given a list selecting tenant A by header, should return A's roles only")
    void given_listSelectingAByHeader_should_returnTheRolesOfAOnly() throws Exception {
      // Act
      List<String> ids = roleIdsOfListOnHeader(tenantA);

      // Assert
      assertThat(ids).contains(roleA).doesNotContain(roleB, roleDefault);
    }

    @Test
    @DisplayName("given a list selecting A and B by header, should return the roles of both")
    void given_listSelectingTwoTenants_should_returnTheRolesOfBoth() throws Exception {
      // Act
      List<String> ids = roleIdsOfListOnHeader(tenantA + "," + tenantB);

      // Assert
      assertThat(ids).contains(roleA, roleB).doesNotContain(roleDefault);
    }

    @Test
    @DisplayName("given a list with no selector, should return the roles of the default tenant")
    void given_listWithNoSelector_should_returnTheRolesOfTheDefaultTenant() throws Exception {
      // Act
      List<String> ids = roleIdsOfListOnHeader(null);

      // Assert
      assertThat(ids).contains(roleDefault).doesNotContain(roleA, roleB);
    }

    @Test
    @DisplayName("given a list under tenant A's path, should return A's roles only")
    void given_listUnderPathOfA_should_returnTheRolesOfAOnly() throws Exception {
      // Act
      String response =
          mvc.perform(get(ROLES_ON_PATH, tenantA).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].role_id");
      assertThat(ids).contains(roleA).doesNotContain(roleB, roleDefault);
    }

    @Test
    @DisplayName("given a read of B's role under A's path, should answer 404")
    void given_readOfRoleOfBUnderPathOfA_should_answerNotFound() throws Exception {
      // Act / Assert
      mvc.perform(get(ROLE_ON_PATH, tenantA, roleB)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given a read of A's role selecting A by header, should find it")
    void given_readOfRoleOfASelectingAByHeader_should_findIt() throws Exception {
      // Arrange
      givenNoAmbientTenant();

      // Act / Assert
      mvc.perform(get(ROLES_ON_HEADER + "/" + roleA).header(TENANT_HEADER, tenantA))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given a read of B's role selecting A by header, should answer 404")
    void given_readOfRoleOfBSelectingAByHeader_should_answerNotFound() throws Exception {
      // Arrange
      givenNoAmbientTenant();

      // Act / Assert
      mvc.perform(get(ROLES_ON_HEADER + "/" + roleB).header(TENANT_HEADER, tenantA))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given an update of A's role selecting A by header, should rename it")
    void given_updateOfRoleOfASelectingAByHeader_should_renameIt() throws Exception {
      // Arrange
      givenNoAmbientTenant();

      // Act
      mvc.perform(
              put(ROLES_ON_HEADER + "/" + roleA)
                  .header(TENANT_HEADER, tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newRole("renamed-in-a")))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertEquals("renamed-in-a", rawNameOf(roleA));
    }

    @Test
    @DisplayName("given an update of B's role selecting A by header, should answer 404")
    void given_updateOfRoleOfBSelectingAByHeader_should_leaveItUntouched() throws Exception {
      // Arrange
      givenNoAmbientTenant();

      // Act
      mvc.perform(
              put(ROLES_ON_HEADER + "/" + roleB)
                  .header(TENANT_HEADER, tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newRole("hijacked")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals("role-of-b", rawNameOf(roleB));
    }

    @Test
    @DisplayName("given a delete of A's role selecting A by header, should delete it")
    void given_deleteOfRoleOfASelectingAByHeader_should_deleteIt() throws Exception {
      // Arrange
      givenNoAmbientTenant();

      // Act
      mvc.perform(delete(ROLES_ON_HEADER + "/" + roleA).header(TENANT_HEADER, tenantA).with(csrf()))
          .andExpect(status().isOk());
      entityManager.flush();

      // Assert
      assertEquals(null, rawTenantOf(roleA));
    }

    @Test
    @DisplayName("given a delete of B's role selecting A by header, should answer 404 and keep it")
    void given_deleteOfRoleOfBSelectingAByHeader_should_keepIt() throws Exception {
      // Arrange
      givenNoAmbientTenant();

      // Act
      mvc.perform(delete(ROLES_ON_HEADER + "/" + roleB).header(TENANT_HEADER, tenantA).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(tenantB, rawTenantOf(roleB));
    }

    @Test
    @DisplayName("given a delete of B's role under A's path, should answer 404 and keep it")
    void given_deleteOfRoleOfBUnderPathOfA_should_keepIt() throws Exception {
      // Act
      mvc.perform(delete(ROLE_ON_PATH, tenantA, roleB).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(tenantB, rawTenantOf(roleB));
    }
  }

  @Nested
  @DisplayName("a caller of A and B with no access to the default tenant")
  class TwoTenantsWithoutDefault {

    @Test
    @DisplayName("given a create with no selector, should refuse it and store nothing")
    void given_createWithNoSelector_should_refuseAndStoreNothing() throws Exception {
      // Act
      createOnHeader(null, "created-without-default", 400);

      // Assert
      assertEquals(0L, rawCountByName("created-without-default"));
    }

    @Test
    @DisplayName("given a create selecting B by header, should store the role in B")
    void given_createSelectingBByHeader_should_storeTheRoleInB() throws Exception {
      // Act
      String response = createOnHeader(tenantB, "created-in-b", 200);

      // Assert
      assertEquals(tenantB, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }
  }

  @Nested
  @DisplayName("a caller of a single tenant")
  class SingleTenant {

    @BeforeEach
    void leaveTenantB() {
      tenantHelper.detachCurrentUserFromTenant(tenantB);
    }

    @Test
    @DisplayName("given a create with no selector, should store the role in that tenant")
    void given_createWithNoSelector_should_storeTheRoleInTheOnlyTenant() throws Exception {
      // Act
      String response = createOnHeader(null, "created-in-the-only-tenant", 200);

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }

    @Test
    @DisplayName("given a list with no selector, should return the roles of that tenant only")
    void given_listWithNoSelector_should_returnTheRolesOfTheOnlyTenant() throws Exception {
      // Act
      List<String> ids = roleIdsOfListOnHeader(null);

      // Assert
      assertThat(ids).contains(roleA).doesNotContain(roleB);
    }
  }

  @Nested
  @DisplayName("a caller with no tenant membership")
  class NoMembership {

    @BeforeEach
    void leaveEveryTenant() {
      tenantHelper.detachCurrentUserFromTenant(tenantA);
      tenantHelper.detachCurrentUserFromTenant(tenantB);
    }

    @Test
    @DisplayName("given a create, should refuse it and store nothing")
    void given_create_should_refuseAndStoreNothing() throws Exception {
      // Act
      createOnHeader(null, "created-without-membership", 400);

      // Assert
      assertEquals(0L, rawCountByName("created-without-membership"));
    }

    @Test
    @DisplayName("given a list, should return no role")
    void given_list_should_returnNoRole() throws Exception {
      // Act
      List<String> ids = roleIdsOfListOnHeader(null);

      // Assert
      assertThat(ids).doesNotContain(roleA, roleB);
    }
  }

  @Nested
  @DisplayName("a caller without the admin flag")
  class NonAdmin {

    private static final Set<Capability> MANAGE_ROLES =
        Set.of(
            Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES,
            Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES);

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("given the capability in A, should create a role in A selecting A by header")
    void given_capabilityInA_should_createTheRoleInA() throws Exception {
      // Arrange
      tenantHelper.grantCapabilitiesInTenant(tenantA, MANAGE_ROLES);

      // Act
      String response = createOnHeader(tenantA, "created-by-non-admin", 200);

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("given the capability in B only, should refuse a create selecting A by header")
    void given_capabilityInBOnly_should_refuseACreateInA() throws Exception {
      // Arrange
      tenantHelper.detachCurrentUserFromTenant(tenantA);
      tenantHelper.grantCapabilitiesInTenant(tenantB, MANAGE_ROLES);

      // Act
      createOnHeader(tenantA, "created-outside-membership", 403);

      // Assert
      assertEquals(0L, rawCountByName("created-outside-membership"));
    }

    // The caller holds BYPASS in the default tenant, which is where the plain route reads rights,
    // and only the pair of MANAGE_ROLES in A, where the role is written.
    private void givenBypassInDefaultAndOnlyManageRolesInA() {
      tenantHelper.grantCapabilitiesInTenant(tenantA, MANAGE_ROLES);
      tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.BYPASS})
    @DisplayName(
        "given a capability held in the default tenant and not in A, should refuse a create"
            + " selecting A by header and store nothing")
    void given_capabilityNotHeldInA_should_refuseACreateInA() throws Exception {
      // Arrange
      givenBypassInDefaultAndOnlyManageRolesInA();
      givenNoAmbientTenant();
      entityManager.flush();
      entityManager.clear();

      // Act
      mvc.perform(
              post(ROLES_ON_HEADER)
                  .header(TENANT_HEADER, tenantA)
                  .accept(MediaType.APPLICATION_JSON)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          new RoleInput("refused-in-a", null, Set.of(Capability.ACCESS_ASSETS))))
                  .with(csrf()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(UNHELD_CAPABILITIES))
          .andExpect(
              jsonPath("$.errors.children.message.errors")
                  .value(hasItem(Capability.ACCESS_ASSETS.name())));

      // Assert
      assertEquals(0L, rawCountByName("refused-in-a"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.BYPASS})
    @DisplayName(
        "given a capability held in the default tenant and not in A, should refuse an update of"
            + " A's role selecting A by header and keep its capabilities")
    void given_capabilityNotHeldInA_should_refuseAnUpdateOfTheRoleOfA() throws Exception {
      // Arrange
      givenBypassInDefaultAndOnlyManageRolesInA();
      Set<String> before = rawCapabilitiesOf(roleA);
      givenNoAmbientTenant();
      entityManager.flush();
      entityManager.clear();

      // Act
      mvc.perform(
              put(ROLES_ON_HEADER + "/" + roleA)
                  .header(TENANT_HEADER, tenantA)
                  .accept(MediaType.APPLICATION_JSON)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          new RoleInput(
                              "refused-rename",
                              null,
                              Set.of(
                                  Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES,
                                  Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES,
                                  Capability.ACCESS_ASSETS))))
                  .with(csrf()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(UNHELD_CAPABILITIES))
          .andExpect(
              jsonPath("$.errors.children.message.errors")
                  .value(hasItem(Capability.ACCESS_ASSETS.name())));

      // Assert
      assertEquals(before, rawCapabilitiesOf(roleA));
      assertEquals("role-of-a", rawNameOf(roleA));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.BYPASS})
    @DisplayName(
        "given only capabilities held in A, should create the role in A selecting A by header")
    void given_onlyCapabilitiesHeldInA_should_createTheRoleInA() throws Exception {
      // Arrange
      givenBypassInDefaultAndOnlyManageRolesInA();
      givenNoAmbientTenant();
      entityManager.flush();
      entityManager.clear();

      // Act
      String response =
          mvc.perform(
                  post(ROLES_ON_HEADER)
                      .header(TENANT_HEADER, tenantA)
                      .accept(MediaType.APPLICATION_JSON)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(newRole("created-with-held-capabilities")))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertEquals(tenantA, rawTenantOf(JsonPath.read(response, "$.role_id")));
    }
  }
}
