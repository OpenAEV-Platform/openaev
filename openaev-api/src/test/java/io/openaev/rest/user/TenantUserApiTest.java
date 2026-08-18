package io.openaev.rest.user;

import static io.openaev.rest.user.TenantUserApi.TENANT_USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforcement of the tenant users, groups and roles triad on the user endpoints, the resource with
 * no API test of its own. The equivalent coverage for groups and roles lives in TenantGroupApiTest
 * and TenantRoleApiTest.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tenant User API")
public class TenantUserApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  private static UserInput userInput() {
    return new UserInput(
        "capability-" + UUID.randomUUID() + "@filigran.io",
        "Capability",
        "Test",
        UserFixture.RAW_PASSWORD,
        null,
        null,
        null,
        null,
        List.of(),
        false,
        List.of());
  }

  private String createUser() throws Exception {
    String response =
        mvc.perform(
                post(tenantUri(TENANT_USER_URI))
                    .content(asJsonString(userInput()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.user_id");
  }

  @Nested
  @DisplayName("Tier enforcement")
  class TierEnforcement {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("ACCESS reads the list but cannot create")
    void given_accessOnly_should_readButNotCreate() throws Exception {
      mvc.perform(get(tenantUri(TENANT_USER_URI)).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      mvc.perform(
              post(tenantUri(TENANT_USER_URI))
                  .content(asJsonString(userInput()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("MANAGE creates but cannot delete")
    void given_manage_should_createButNotDelete() throws Exception {
      String userId = createUser();

      mvc.perform(delete(tenantUri(TENANT_USER_URI) + "/" + userId).with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("DELETE covers the whole chain, parents included")
    void given_delete_should_createAndDelete() throws Exception {
      String userId = createUser();

      mvc.perform(delete(tenantUri(TENANT_USER_URI) + "/" + userId).with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_ASSETS})
    @DisplayName("An unrelated capability reaches nothing")
    void given_unrelatedCapability_should_beForbidden() throws Exception {
      mvc.perform(get(tenantUri(TENANT_USER_URI)).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Split from tenant settings")
  class SplitFromTenantSettings {

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_SETTINGS})
    @DisplayName("Tenant settings alone no longer reaches the user endpoints")
    void given_tenantSettingsOnly_should_beForbidden() throws Exception {
      // The whole point of the split: DELETE_TENANT_SETTINGS resolves its parents too, so this
      // covers the three tiers at once.
      mvc.perform(get(tenantUri(TENANT_USER_URI)).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());

      mvc.perform(
              post(tenantUri(TENANT_USER_URI))
                  .content(asJsonString(userInput()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Cross-tenant isolation")
  class CrossTenantIsolation {

    @Test
    @WithMockUser
    @DisplayName("A user created in tenant X is not readable from tenant Y")
    void given_userInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // Identical capabilities on both sides: only the tenant-scoped role assignment may decide.
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              java.util.Set.of(
                  Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES,
                  Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", java.util.Set.of(Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES));

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/users")
                      .content(asJsonString(userInput()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String userId = JsonPath.read(createResponse, "$.user_id");

      int status =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/users/" + userId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      assertTrue(
          status == 403 || status == 404,
          "Expected 403 or 404 but got " + status + " - cross-tenant user read was NOT blocked");
    }

    @Test
    @WithMockUser
    @DisplayName("Listing in tenant Y never returns tenant X's users")
    void given_userInTenantX_should_notBeListedInTenantY() throws Exception {
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              java.util.Set.of(
                  Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES,
                  Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", java.util.Set.of(Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES));

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/users")
                      .content(asJsonString(userInput()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String userId = JsonPath.read(createResponse, "$.user_id");

      String listResponse =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/users")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertEquals(
          0,
          JsonPath.<List<String>>read(listResponse, "$[?(@.user_id == '" + userId + "')]").size(),
          "Tenant X's user leaked into tenant Y's list");
    }
  }
}
