package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.ZipUtils;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workflow of a chained scenario is read and written in the scenario's own tenant, whatever the
 * caller's scope: once access to the scenario is granted, its detail names its workflow, its
 * duplicate gets a copy of its workflow, its export carries its workflow, and its launch gives the
 * simulation a workflow.
 *
 * <p>The scenario lives in the default tenant. Each call is sent on the non-prefixed route by a
 * caller whose scope excludes that tenant (a platform admin who is a member of another tenant only,
 * or of no tenant at all) and by a member of the scenario's tenant: the outcome is the same.
 *
 * <p>The production active tables are armed, so the class follows what production enforces.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("The workflow of a chained scenario follows the scenario's tenant")
class ChainedScenarioWorkflowFollowsItsTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager manager;

  // A chained duplicate is an Enterprise Edition feature.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void before() {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    scenarioComposer.reset();
    injectComposer.reset();
    stepComposer.reset();
    tagComposer.reset();
    workflowComposer.reset();
  }

  private record ChainedScenario(Scenario scenario, Workflow template, Tag tag) {}

  /** A chained scenario of the default tenant: its workflow template, one inject and one tag. */
  private ChainedScenario chainedScenario() {
    return chainedScenario(false);
  }

  /**
   * The same scenario with one step on its workflow template: the export must find that step
   * already loaded, which only holds while the graph is read in the scenario's tenant. Only the
   * export asks for a step: a launch would enqueue it, which needs a broker publisher channel the
   * test stack does not open.
   */
  private ChainedScenario chainedScenarioWithAStep() {
    return chainedScenario(true);
  }

  /**
   * The tag is an ordinary row of a tenant-active table the export serialises, read under the scope
   * the request resolved and not under the scenario's tenant.
   */
  private ChainedScenario chainedScenario(boolean withAStep) {
    Tag tag = TagFixture.getTagWithText("chained-scenario-export");
    ScenarioComposer.Composer scenario =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultCrisisScenario())
            .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
            .withTag(tagComposer.forTag(tag));
    WorkflowComposer.Composer workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withScenario(scenario);
    if (withAStep) {
      workflow.withStep(stepComposer.forStep(StepFixture.getDefaultStepTemplate()));
    }
    Workflow template = workflow.persist().get();
    manager.flush();
    manager.clear();
    return new ChainedScenario(scenario.get(), template, tag);
  }

  /** Makes the current caller a member of another tenant than the scenario's, and of no other. */
  private void joinOtherTenantOnly() throws Exception {
    Tenant other = tenantHelper.createTenant("chained-scenario-other");
    tenantHelper.attachCurrentUserToTenant(other.getId());
  }

  /**
   * The request thread of the non-prefixed route carries no ambient tenant, while the fixtures
   * leave one on the test thread: give the test thread the request's state before the call.
   */
  private static void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  /** Counts the workflows of a scenario or simulation, read in the default tenant. */
  private long workflowsOf(String ownerColumn, String ownerId) {
    DefaultTenantScopeTestListener.setAmbientScope(manager, Tenant.DEFAULT_TENANT_UUID);
    return ((Number)
            manager
                .createNativeQuery("SELECT count(*) FROM workflows WHERE " + ownerColumn + " = :id")
                .setParameter("id", ownerId)
                .getSingleResult())
        .longValue();
  }

  private String detail(String scenarioId) throws Exception {
    clearAmbientTenant();
    return mvc.perform(get(SCENARIO_URI + "/" + scenarioId).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String postAndReadId(String uri, String idPath) throws Exception {
    clearAmbientTenant();
    String response =
        mvc.perform(post(uri).accept(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, idPath);
  }

  private String exportJson(Scenario scenario) throws Exception {
    clearAmbientTenant();
    byte[] zip =
        mvc.perform(
                get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    return ZipUtils.getZipEntry(
        zip, "%s.json".formatted(scenario.getName()), ZipUtils::streamToString);
  }

  /** A chained export carries the workflow instead of the runtime injects. */
  private static void assertExportedAsChained(String json) {
    assertThatJson(json).node("scenario_workflow").isPresent();
    // The workflow graph is loaded in the scenario's tenant, so the serialization finds its steps:
    // an empty collection here would mean they were read under the request's scope.
    assertThatJson(json).node("scenario_workflow.workflow_steps").isArray().hasSize(1);
    assertThatJson(json).node("scenario_injects").isArray().isEmpty();
  }

  /**
   * The scenario's tags are read under the request's scope: a scope without its tenant sees none.
   */
  private static void assertNoTagRead(String json) {
    assertThatJson(json).node("scenario_tags").isArray().isEmpty();
  }

  /** A caller scoped to the scenario's tenant reads its tags as it reads any row of that tenant. */
  private static void assertTagRead(String json, Tag tag) {
    assertThatJson(json).node("scenario_tags").isArray().hasSize(1);
    assertThatJson(json).node("scenario_tags[0].tag_id").isEqualTo(tag.getId());
  }

  @Nested
  @DisplayName("Detail")
  class Detail {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should name its workflow")
    void given_scopeWithoutItsTenant_should_nameItsWorkflow() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedScenario rows = chainedScenario();

      // Act
      String json = detail(rows.scenario().getId());

      // Assert
      assertThatJson(json).node("scenario_workflow_id").isEqualTo(rows.template().getId());
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should name its workflow")
    void given_membershipOfItsTenant_should_nameItsWorkflow() throws Exception {
      // Arrange
      ChainedScenario rows = chainedScenario();

      // Act
      String json = detail(rows.scenario().getId());

      // Assert
      assertThatJson(json).node("scenario_workflow_id").isEqualTo(rows.template().getId());
    }
  }

  @Nested
  @DisplayName("Duplicate")
  class Duplicate {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should copy its workflow")
    void given_scopeWithoutItsTenant_should_copyItsWorkflow() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedScenario rows = chainedScenario();

      // Act
      String duplicateId =
          postAndReadId(SCENARIO_URI + "/" + rows.scenario().getId(), "$.scenario_id");

      // Assert
      assertThat(workflowsOf("workflow_scenario_id", duplicateId)).isEqualTo(1);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should copy its workflow")
    void given_membershipOfItsTenant_should_copyItsWorkflow() throws Exception {
      // Arrange
      ChainedScenario rows = chainedScenario();

      // Act
      String duplicateId =
          postAndReadId(SCENARIO_URI + "/" + rows.scenario().getId(), "$.scenario_id");

      // Assert
      assertThat(workflowsOf("workflow_scenario_id", duplicateId)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Launch")
  class Launch {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should launch a chained simulation")
    void given_scopeWithoutItsTenant_should_launchAChainedSimulation() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedScenario rows = chainedScenario();

      // Act
      String simulationId =
          postAndReadId(
              SCENARIO_URI + "/" + rows.scenario().getId() + "/exercise/running", "$.exercise_id");

      // Assert
      assertThat(workflowsOf("workflow_simulation_id", simulationId)).isPositive();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should launch a chained simulation")
    void given_membershipOfItsTenant_should_launchAChainedSimulation() throws Exception {
      // Arrange
      ChainedScenario rows = chainedScenario();

      // Act
      String simulationId =
          postAndReadId(
              SCENARIO_URI + "/" + rows.scenario().getId() + "/exercise/running", "$.exercise_id");

      // Assert
      assertThat(workflowsOf("workflow_simulation_id", simulationId)).isPositive();
    }
  }

  @Nested
  @DisplayName("Export")
  class Export {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should export it as chained")
    void given_scopeWithoutItsTenant_should_exportItAsChained() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedScenario rows = chainedScenarioWithAStep();

      // Act
      String json = exportJson(rows.scenario());

      // Assert
      assertExportedAsChained(json);
      assertNoTagRead(json);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given no tenant membership should export it as chained")
    void given_noTenantMembership_should_exportItAsChained() throws Exception {
      // Arrange
      ChainedScenario rows = chainedScenarioWithAStep();

      // Act
      String json = exportJson(rows.scenario());

      // Assert
      assertExportedAsChained(json);
      assertNoTagRead(json);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should export it as chained")
    void given_membershipOfItsTenant_should_exportItAsChained() throws Exception {
      // Arrange
      ChainedScenario rows = chainedScenarioWithAStep();

      // Act
      String json = exportJson(rows.scenario());

      // Assert
      assertExportedAsChained(json);
      assertTagRead(json, rows.tag());
    }
  }
}
