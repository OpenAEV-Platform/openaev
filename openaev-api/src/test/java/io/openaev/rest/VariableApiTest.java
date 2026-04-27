package io.openaev.rest;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Variable;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.VariableRepository;
import io.openaev.rest.variable.form.VariableInput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
public class VariableApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioService scenarioService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private VariableRepository variableRepository;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private TenantComposer tenantComposer;

  static String VARIABLE_ID;
  static String SCENARIO_ID;

  @AfterAll
  void afterAll() {
    if (SCENARIO_ID != null) {
      this.scenarioRepository.deleteById(SCENARIO_ID);
    }
    if (VARIABLE_ID != null) {
      this.variableRepository.deleteById(VARIABLE_ID);
    }
  }

  // -- SCENARIOS --

  @DisplayName("Create variable for scenario succeed")
  @Test
  @Order(1)
  @WithMockUser(isAdmin = true)
  void createVariableForScenarioTest() throws Exception {
    // -- PREPARE --
    Scenario scenario = new Scenario();
    scenario.setName("Scenario name");
    Scenario scenarioCreated = this.scenarioService.createScenario(scenario);
    SCENARIO_ID = scenarioCreated.getId();
    Variable variable = new Variable();

    // -- EXECUTE & ASSERT --
    this.mvc
        .perform(
            post(SCENARIO_URI + "/" + SCENARIO_ID + "/variables")
                .content(asJsonString(variable))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError());

    // -- PREPARE --
    String variableKey = "key";
    variable.setKey(variableKey);
    variable.setScenario(scenario);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/variables")
                    .content(asJsonString(variable))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.variable_key").value(variableKey))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    VARIABLE_ID = JsonPath.read(response, "$.variable_id");
  }

  @DisplayName("Retrieve variables for scenario")
  @Test
  @Order(2)
  @WithMockUser(isAdmin = true)
  void retrieveVariableForScenarioTest() throws Exception {
    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + SCENARIO_ID + "/variables")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Update variable for scenario")
  @Test
  @Order(3)
  @WithMockUser(isAdmin = true)
  void updateVariableForScenarioTest() throws Exception {
    // -- PREPARE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + SCENARIO_ID + "/variables")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Variable variable = new Variable();
    String variableValue = "variable-value";
    variable.setKey(JsonPath.read(response, "$[0].variable_key"));
    variable.setValue("variable-value");

    // -- EXECUTE --
    response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/" + SCENARIO_ID + "/variables/" + VARIABLE_ID)
                    .content(asJsonString(variable))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(variableValue, JsonPath.read(response, "$.variable_value"));
  }

  @DisplayName("Delete variable for scenario")
  @Test
  @Order(4)
  @WithMockUser(isAdmin = true)
  void deleteVariableForScenarioTest() throws Exception {
    // -- EXECUTE 1 ASSERT --
    this.mvc
        .perform(delete(SCENARIO_URI + "/" + SCENARIO_ID + "/variables/" + VARIABLE_ID))
        .andExpect(status().is2xxSuccessful());
  }

  @Nested
  @DisplayName("Tenant isolation on scenario variables")
  @Transactional
  class TenantIsolation {

    @Test
    @DisplayName(
        "given scenario in Tenant XXX, when create variable from Tenant YYY, should return 404")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_createVariableFromTenantYYY_should_return404()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      Tenant tenantYYY =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant YYY")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();

      // Act — switch to Tenant YYY and try to create a variable
      TenantContext.setCurrentTenant(tenantYYY.getId());
      VariableInput input = new VariableInput();
      input.setKey("cross_tenant_key");

      // Assert
      mvc.perform(
              post(SCENARIO_URI + "/" + scenarioId + "/variables")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());

      // Cleanup
      TenantContext.setCurrentTenant(tenantXXX.getId());
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName(
        "given scenario with variable in Tenant XXX, when list variables from same tenant, should return 200")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_listVariablesFromSameTenant_should_return200()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      Variable variable = new Variable();
      variable.setKey("same_tenant_var");
      variable.setScenario(scenario);
      variableRepository.save(variable);
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();

      // Act & Assert — reading from same tenant should succeed
      mvc.perform(
              get(SCENARIO_URI + "/" + scenarioId + "/variables")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$[0].variable_key").value("same_tenant_var"));

      // Cleanup
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName(
        "given scenario with variable in Tenant XXX, when list variables from Tenant YYY, should return 404")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_listVariablesFromTenantYYY_should_return404()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      Tenant tenantYYY =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant YYY")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      Variable variable = new Variable();
      variable.setKey("tenant_var");
      variable.setScenario(scenario);
      variableRepository.save(variable);
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();

      // Act — switch to Tenant YYY
      TenantContext.setCurrentTenant(tenantYYY.getId());

      // Assert
      mvc.perform(
              get(SCENARIO_URI + "/" + scenarioId + "/variables")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());

      // Cleanup
      TenantContext.setCurrentTenant(tenantXXX.getId());
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName(
        "given scenario with variable in Tenant XXX, when update variable from Tenant YYY, should return 404")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_updateVariableFromTenantYYY_should_return404()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      Tenant tenantYYY =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant YYY")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      Variable variable = new Variable();
      variable.setKey("update_var");
      variable.setScenario(scenario);
      variable = variableRepository.save(variable);
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();
      String variableId = variable.getId();

      // Act — switch to Tenant YYY
      TenantContext.setCurrentTenant(tenantYYY.getId());
      VariableInput input = new VariableInput();
      input.setKey("update_var");
      input.setValue("hacked");

      // Assert
      mvc.perform(
              put(SCENARIO_URI + "/" + scenarioId + "/variables/" + variableId)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());

      // Cleanup
      TenantContext.setCurrentTenant(tenantXXX.getId());
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName(
        "given scenario with variable in Tenant XXX, when delete variable from Tenant YYY, should return 404")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_deleteVariableFromTenantYYY_should_return404()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      Tenant tenantYYY =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant YYY")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      Variable variable = new Variable();
      variable.setKey("delete_var");
      variable.setScenario(scenario);
      variable = variableRepository.save(variable);
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();
      String variableId = variable.getId();

      // Act — switch to Tenant YYY
      TenantContext.setCurrentTenant(tenantYYY.getId());

      // Assert
      mvc.perform(delete(SCENARIO_URI + "/" + scenarioId + "/variables/" + variableId))
          .andExpect(status().isNotFound());

      // Cleanup
      TenantContext.setCurrentTenant(tenantXXX.getId());
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }
  }
}
