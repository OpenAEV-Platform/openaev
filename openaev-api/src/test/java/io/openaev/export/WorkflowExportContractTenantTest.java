package io.openaev.export;

import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.ZipUtils;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A chained export embeds the contract each step points at, resolved in the tenant of the exported
 * scenario or simulation. The built-in contract id repeats across tenants (#8485) and the export
 * endpoints take a bare {@code TxCtx}, so a caller in several tenants with no selector reaches the
 * export with a scope spanning all of them: a lookup by id alone finds one copy per tenant and the
 * enrichment is silently skipped (it logs and carries on).
 *
 * <p>Each tenant's copy carries its own label, so an export that took the wrong copy, or none,
 * fails on the label rather than on a status code.
 */
@Transactional
@DisplayName("A chained export embeds the contracts of the exported row's own tenant")
@WithMockUser(isAdmin = true)
class WorkflowExportContractTenantTest extends ProductionTablesIntegrationTest {

  private static final String DEFAULT_LABEL = "default-tenant-copy";
  private static final String OTHER_LABEL = "other-tenant-copy";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private EntityManager manager;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;

  @BeforeEach
  void setUp() throws Exception {
    scenarioComposer.reset();
    exerciseComposer.reset();
    workflowComposer.reset();
    stepComposer.reset();
    // A caller in the default tenant and in another one, with the built-in contract in both, each
    // copy labelled after its tenant.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    String otherTenantId = tenantHelper.createTenantWithCurrentUser("export-contract").getId();
    builtinConnectorRegistration.register(emailInjectorIntegrationFactory, otherTenantId);
    builtinConnectorRegistration.register(
        emailInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    labelContract(Tenant.DEFAULT_TENANT_UUID, DEFAULT_LABEL);
    labelContract(otherTenantId, OTHER_LABEL);
  }

  @Nested
  @DisplayName("Scenario export")
  class ScenarioExport {

    @Test
    @DisplayName(
        "given_noSelectorAndTheBuiltInContractInBothTenants_should_embedTheScenarioTenantCopy")
    void given_noSelectorAndTheBuiltInContractInBothTenants_should_embedTheScenarioTenantCopy()
        throws Exception {
      // -- ARRANGE --
      ScenarioComposer.Composer scenarioRef =
          scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());
      workflowComposer
          .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
          .withScenario(scenarioRef)
          .withStep(stepComposer.forStep(stepOnTheBuiltInContract()))
          .persist();
      Scenario scenario = scenarioRef.get();
      manager.flush();
      manager.clear();

      // -- ACT --
      byte[] response =
          mvc.perform(get(SCENARIO_URI + "/" + scenario.getId() + "/export"))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      // -- ASSERT --
      JsonNode export = mapper.readTree(exportJson(response, scenario.getName()));
      assertThat(embeddedContractLabel(export.get("scenario_workflow"))).isEqualTo(DEFAULT_LABEL);
    }
  }

  @Nested
  @DisplayName("Simulation export")
  class SimulationExport {

    @Test
    @DisplayName(
        "given_noSelectorAndTheBuiltInContractInBothTenants_should_embedTheSimulationTenantCopy")
    void given_noSelectorAndTheBuiltInContractInBothTenants_should_embedTheSimulationTenantCopy()
        throws Exception {
      // -- ARRANGE --
      ExerciseComposer.Composer exerciseRef =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultCrisisExercise());
      workflowComposer
          .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
          .withSimulation(exerciseRef)
          .withStep(stepComposer.forStep(stepOnTheBuiltInContract()))
          .persist();
      Exercise exercise = exerciseRef.get();
      manager.flush();
      manager.clear();

      // -- ACT --
      byte[] response =
          mvc.perform(get(EXERCISE_URI + "/" + exercise.getId() + "/export"))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      // -- ASSERT --
      JsonNode export = mapper.readTree(exportJson(response, exercise.getName()));
      assertThat(embeddedContractLabel(export.get("exercise_workflow"))).isEqualTo(DEFAULT_LABEL);
    }
  }

  // -- Helpers --

  private static Step stepOnTheBuiltInContract() {
    Step step = StepFixture.getDefaultStepTemplate();
    step.setData(
        "{\"inject_injector_contract\":{\"injector_contract_id\":\"%s\"}}"
            .formatted(EMAIL_DEFAULT));
    return step;
  }

  private void labelContract(String tenantId, String label) {
    manager
        .createNativeQuery(
            "UPDATE injectors_contracts SET injector_contract_labels = hstore('en', :label)"
                + " WHERE injector_contract_id = :id AND tenant_id = :tenantId")
        .setParameter("label", label)
        .setParameter("id", EMAIL_DEFAULT)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
  }

  private static String embeddedContractLabel(JsonNode workflow) {
    JsonNode stepData = workflow.get("workflow_steps").get(0).get("step_data");
    JsonNode data = stepData.isTextual() ? parse(stepData.asText()) : stepData;
    return data.get("inject_injector_contract")
        .path("injector_contract_labels")
        .path("en")
        .asText();
  }

  private static JsonNode parse(String json) {
    try {
      return new ObjectMapper().readTree(json);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String exportJson(byte[] zip, String entryName) throws IOException {
    return ZipUtils.getZipEntry(zip, "%s.json".formatted(entryName), ZipUtils::streamToString);
  }
}
