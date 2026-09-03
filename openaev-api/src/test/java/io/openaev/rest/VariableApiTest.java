package io.openaev.rest;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Variable;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.VariableRepository;
import io.openaev.rest.variable.form.VariableInput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
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
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  static String VARIABLE_ID;
  static String SCENARIO_ID;

  @AfterAll
  void afterAll() {
    this.scenarioRepository.deleteById(SCENARIO_ID);
    this.variableRepository.deleteById(VARIABLE_ID);
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
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
        .perform(
            delete(SCENARIO_URI + "/" + SCENARIO_ID + "/variables/" + VARIABLE_ID).with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  // -- TENANT ISOLATION TESTS --

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  @Transactional
  class TenantIsolation {

    private VariableInput createVariableInput(String key) {
      VariableInput input = new VariableInput();
      input.setKey(key);
      input.setValue("test_value");
      return input;
    }

    private String createVariableInScenario(String tenantId, String scenarioId, String key)
        throws Exception {
      String response =
          mvc.perform(
                  post("/api/tenants/" + tenantId + "/scenarios/" + scenarioId + "/variables")
                      .content(asJsonString(createVariableInput(key)))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$.variable_id");
    }

    private String createScenarioInTenant(String tenantId) throws Exception {
      String response =
          mvc.perform(
                  post("/api/tenants/" + tenantId + "/scenarios")
                      .content("{\"scenario_name\":\"Isolation Scenario\"}")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$.scenario_id");
    }

    @Test
    @DisplayName("Variable in scenario X should NOT be updatable via scenario Y (cross-tenant)")
    void given_variableInScenarioX_should_notBeUpdatableViaScenarioY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      String scenarioX = createScenarioInTenant(tenantX.getId());
      String scenarioY = createScenarioInTenant(tenantY.getId());
      String variableId = createVariableInScenario(tenantX.getId(), scenarioX, "isolation_key");

      entityManager.flush();
      entityManager.clear();

      // Act — try to update variable using tenant Y's scenario ID
      int responseStatus =
          mvc.perform(
                  put("/api/tenants/"
                          + tenantY.getId()
                          + "/scenarios/"
                          + scenarioY
                          + "/variables/"
                          + variableId)
                      .content(asJsonString(createVariableInput("hijacked_key")))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Variable in scenario X should be updatable via same scenario X")
    void given_variableInScenarioX_should_beUpdatableViaScenarioX() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      String scenarioX = createScenarioInTenant(tenantX.getId());
      String variableId = createVariableInScenario(tenantX.getId(), scenarioX, "same_tenant_key");

      // Act — update using same tenant/scenario
      String response =
          mvc.perform(
                  put("/api/tenants/"
                          + tenantX.getId()
                          + "/scenarios/"
                          + scenarioX
                          + "/variables/"
                          + variableId)
                      .content(asJsonString(createVariableInput("updated_key")))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertEquals("updated_key", JsonPath.read(response, "$.variable_key"));
    }

    @Test
    @DisplayName("Variable in scenario X should NOT be deletable via scenario Y (cross-tenant)")
    void given_variableInScenarioX_should_notBeDeletableViaScenarioY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      String scenarioX = createScenarioInTenant(tenantX.getId());
      String scenarioY = createScenarioInTenant(tenantY.getId());
      String variableId = createVariableInScenario(tenantX.getId(), scenarioX, "delete_iso_key");

      entityManager.flush();
      entityManager.clear();

      // Act — try to delete variable using tenant Y's scenario ID
      int responseStatus =
          mvc.perform(
                  delete(
                          "/api/tenants/"
                              + tenantY.getId()
                              + "/scenarios/"
                              + scenarioY
                              + "/variables/"
                              + variableId)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
