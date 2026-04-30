package io.openaev.rest.scenario;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.rest.scenario.form.ScenarioInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tenant isolation tests for Scenario API.
 *
 * <p>Uses {@code @Transactional} (rolled back after each test) with explicit {@code
 * switchToTenant()} calls to set the RLS session variable ({@code app.current_tenant}) on the
 * shared connection before each MockMvc request. This is necessary because MockMvc dispatches
 * requests in the same thread/transaction, so the interceptor-based tenant resolution does not
 * trigger a new connection checkout.
 */
@Transactional
@DisplayName("Scenario API — Tenant isolation")
public class ScenarioTenantIsolationApiTest extends IntegrationTest {

  private static final String TENANT_SCENARIO_URI = "/api/tenants/%s/scenarios";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager em;

  @Test
  @DisplayName(
      "given scenario in Tenant XXX, when getScenarioById from Tenant YYY, should return 404")
  @WithMockUser(isAdmin = true)
  void given_scenarioInTenantXXX_when_getByIdFromTenantYYY_should_return404() throws Exception {
    // -- ARRANGE --
    Tenant tenantXXX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant XXX");
    Tenant tenantYYY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant YYY");

    // Switch to tenant XXX and create scenario via REST API
    tenantIsolationHelper.switchToTenant(tenantXXX.getId(), em);
    ScenarioInput scenarioInput = new ScenarioInput();
    scenarioInput.setName("Isolated scenario");
    String createResponse =
        mvc.perform(
                post(TENANT_SCENARIO_URI.formatted(tenantXXX.getId()))
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

    // -- ACT & ASSERT: switch to tenant YYY — scenario should NOT be visible --
    tenantIsolationHelper.switchToTenant(tenantYYY.getId(), em);
    mvc.perform(
            get(TENANT_SCENARIO_URI.formatted(tenantYYY.getId()) + "/" + scenarioId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "given scenario in Tenant XXX, when getScenarioById from same tenant, should return 200")
  @WithMockUser(isAdmin = true)
  void given_scenarioInTenantXXX_when_getByIdFromSameTenant_should_return200() throws Exception {
    // -- ARRANGE --
    Tenant tenantXXX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant XXX");

    // Switch to tenant XXX and create scenario via REST API
    tenantIsolationHelper.switchToTenant(tenantXXX.getId(), em);
    ScenarioInput scenarioInput = new ScenarioInput();
    scenarioInput.setName("Visible scenario");
    String createResponse =
        mvc.perform(
                post(TENANT_SCENARIO_URI.formatted(tenantXXX.getId()))
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

    // -- ACT & ASSERT: same tenant — scenario IS visible --
    tenantIsolationHelper.switchToTenant(tenantXXX.getId(), em);
    mvc.perform(
            get(TENANT_SCENARIO_URI.formatted(tenantXXX.getId()) + "/" + scenarioId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName("given scenario in Tenant XXX, when update from Tenant YYY, should return 404")
  @WithMockUser(isAdmin = true)
  void given_scenarioInTenantXXX_when_updateFromTenantYYY_should_return404() throws Exception {
    // -- ARRANGE --
    Tenant tenantXXX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant XXX");
    Tenant tenantYYY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant YYY");

    // Switch to tenant XXX and create scenario via REST API
    tenantIsolationHelper.switchToTenant(tenantXXX.getId(), em);
    ScenarioInput scenarioInput = new ScenarioInput();
    scenarioInput.setName("Protected scenario");
    String createResponse =
        mvc.perform(
                post(TENANT_SCENARIO_URI.formatted(tenantXXX.getId()))
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

    // -- ACT & ASSERT: switch to tenant YYY — update should fail --
    tenantIsolationHelper.switchToTenant(tenantYYY.getId(), em);
    ScenarioInput updateInput = new ScenarioInput();
    updateInput.setName("Updated name");
    mvc.perform(
            put(TENANT_SCENARIO_URI.formatted(tenantYYY.getId()) + "/" + scenarioId)
                .content(asJsonString(updateInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given scenario in Tenant XXX, when delete from Tenant YYY, should not delete it")
  @WithMockUser(isAdmin = true)
  void given_scenarioInTenantXXX_when_deleteFromTenantYYY_should_notDeleteIt() throws Exception {
    // -- ARRANGE --
    Tenant tenantXXX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant XXX");
    Tenant tenantYYY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant YYY");

    // Switch to tenant XXX and create scenario via REST API
    tenantIsolationHelper.switchToTenant(tenantXXX.getId(), em);
    ScenarioInput scenarioInput = new ScenarioInput();
    scenarioInput.setName("Undeletable scenario");
    String createResponse =
        mvc.perform(
                post(TENANT_SCENARIO_URI.formatted(tenantXXX.getId()))
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

    // -- ACT: switch to tenant YYY — delete is a no-op (RLS hides the resource) --
    tenantIsolationHelper.switchToTenant(tenantYYY.getId(), em);
    mvc.perform(
            delete(TENANT_SCENARIO_URI.formatted(tenantYYY.getId()) + "/" + scenarioId)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    // -- ASSERT: switch back to tenant XXX — scenario still exists --
    tenantIsolationHelper.switchToTenant(tenantXXX.getId(), em);
    mvc.perform(
            get(TENANT_SCENARIO_URI.formatted(tenantXXX.getId()) + "/" + scenarioId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }
}
