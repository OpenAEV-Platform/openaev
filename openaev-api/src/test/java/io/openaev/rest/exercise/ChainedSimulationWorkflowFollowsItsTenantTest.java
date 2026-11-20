package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
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
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.ZipUtils;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workflow of a chained simulation is read and written in the simulation's own tenant: once
 * access to the simulation is granted, its detail names its workflow, its duplicate gets a copy of
 * its workflow, and its export carries its workflow.
 *
 * <p>The simulation lives in the default tenant. Each call is sent on the non-prefixed route by a
 * member of the simulation's tenant, by a caller whose scope excludes that tenant (a platform admin
 * who is a member of another tenant only) and by a caller with no membership at all. A by-id load
 * is resolved in the request scope, and an empty scope reaches nothing now that {@code exercises}
 * is isolated, so neither of those two callers finds the simulation.
 *
 * <p>The production active tables are armed, so the class follows what production enforces.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("The workflow of a chained simulation follows the simulation's tenant")
class ChainedSimulationWorkflowFollowsItsTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer exerciseComposer;
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
    exerciseComposer.reset();
    injectComposer.reset();
    stepComposer.reset();
    tagComposer.reset();
    workflowComposer.reset();
  }

  private record ChainedSimulation(Exercise simulation, Workflow template, Tag tag) {}

  /** A chained simulation of the default tenant: its workflow template, one inject and one tag. */
  private ChainedSimulation chainedSimulation() {
    return chainedSimulation(false);
  }

  /**
   * The same simulation with one step on its workflow template: the export must find that step
   * already loaded, which only holds while the graph is read in the simulation's tenant.
   */
  private ChainedSimulation chainedSimulationWithAStep() {
    return chainedSimulation(true);
  }

  /**
   * The tag is an ordinary row of a tenant-active table the export serialises, read under the scope
   * the request resolved and not under the simulation's tenant.
   */
  private ChainedSimulation chainedSimulation(boolean withAStep) {
    Tag tag = TagFixture.getTagWithText("chained-simulation-export");
    ExerciseComposer.Composer simulation =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultCrisisExercise())
            .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
            .withTag(tagComposer.forTag(tag));
    WorkflowComposer.Composer workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulation);
    if (withAStep) {
      workflow.withStep(stepComposer.forStep(StepFixture.getDefaultStepTemplate()));
    }
    Workflow template = workflow.persist().get();
    manager.flush();
    manager.clear();
    return new ChainedSimulation(simulation.get(), template, tag);
  }

  /** Makes the current caller a member of another tenant than the simulation's, and of no other. */
  private void joinOtherTenantOnly() throws Exception {
    Tenant other = tenantHelper.createTenant("chained-simulation-other");
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

  /** Counts the workflow templates of a simulation, read in the simulation's tenant. */
  private long templatesOf(String simulationId) {
    DefaultTenantScopeTestListener.setAmbientScope(manager, Tenant.DEFAULT_TENANT_UUID);
    return ((Number)
            manager
                .createNativeQuery(
                    "SELECT count(*) FROM workflows WHERE workflow_simulation_id = :id"
                        + " AND workflow_status = 'TEMPLATE'")
                .setParameter("id", simulationId)
                .getSingleResult())
        .longValue();
  }

  private String detail(String simulationId) throws Exception {
    clearAmbientTenant();
    return mvc.perform(get(EXERCISE_URI + "/" + simulationId).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String duplicate(String simulationId) throws Exception {
    clearAmbientTenant();
    String response =
        mvc.perform(
                post(EXERCISE_URI + "/" + simulationId)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.exercise_id");
  }

  private record Exported(String json, String fileName) {}

  private Exported export(Exercise simulation) throws Exception {
    clearAmbientTenant();
    MockHttpServletResponse response =
        mvc.perform(
                get(EXERCISE_URI + "/" + simulation.getId() + "/export")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    String json =
        ZipUtils.getZipEntry(
            response.getContentAsByteArray(),
            "%s.json".formatted(simulation.getName()),
            ZipUtils::streamToString);
    return new Exported(json, response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
  }

  /** A chained export carries the workflow instead of the runtime injects, and says so. */
  private static void assertExportedAsChained(Exported exported) {
    assertThatJson(exported.json()).node("exercise_workflow").isPresent();
    // The workflow graph is loaded in the simulation's tenant, so the serialization finds its
    // steps: an empty collection here would mean they were read under the request's scope.
    assertThatJson(exported.json()).node("exercise_workflow.workflow_steps").isArray().hasSize(1);
    assertThatJson(exported.json()).node("exercise_injects").isArray().isEmpty();
    assertThat(exported.fileName()).contains("with_scope_definition");
  }

  /**
   * The simulation's tags are read under the request's scope: a scope without its tenant sees none.
   */
  private static void assertNoTagRead(Exported exported) {
    assertThatJson(exported.json()).node("exercise_tags").isArray().isEmpty();
  }

  /**
   * A caller scoped to the simulation's tenant reads its tags as it reads any row of that tenant.
   */
  private static void assertTagRead(Exported exported, Tag tag) {
    assertThatJson(exported.json()).node("exercise_tags").isArray().hasSize(1);
    assertThatJson(exported.json()).node("exercise_tags[0].tag_id").isEqualTo(tag.getId());
  }

  @Nested
  @DisplayName("Detail")
  class Detail {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should not reach the simulation")
    void given_scopeWithoutItsTenant_should_notReachTheSimulation() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedSimulation rows = chainedSimulation();
      clearAmbientTenant();

      // Act & Assert
      mvc.perform(
              get(EXERCISE_URI + "/" + rows.simulation().getId())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should name its workflow")
    void given_membershipOfItsTenant_should_nameItsWorkflow() throws Exception {
      // Arrange
      ChainedSimulation rows = chainedSimulation();

      // Act
      String json = detail(rows.simulation().getId());

      // Assert
      assertThatJson(json).node("exercise_workflow_id").isEqualTo(rows.template().getId());
    }
  }

  @Nested
  @DisplayName("Duplicate")
  class Duplicate {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should not reach the simulation")
    void given_scopeWithoutItsTenant_should_notReachTheSimulation() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedSimulation rows = chainedSimulation();
      clearAmbientTenant();

      // Act & Assert
      mvc.perform(
              post(EXERCISE_URI + "/" + rows.simulation().getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should copy its workflow")
    void given_membershipOfItsTenant_should_copyItsWorkflow() throws Exception {
      // Arrange
      ChainedSimulation rows = chainedSimulation();

      // Act
      String duplicateId = duplicate(rows.simulation().getId());

      // Assert
      assertThat(templatesOf(duplicateId)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Export")
  class Export {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should not reach the simulation")
    void given_scopeWithoutItsTenant_should_notReachTheSimulation() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedSimulation rows = chainedSimulation();
      clearAmbientTenant();

      // Act & Assert
      mvc.perform(
              get(EXERCISE_URI + "/" + rows.simulation().getId() + "/export")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given no tenant membership should not reach the simulation")
    void given_noTenantMembership_should_notReachTheSimulation() throws Exception {
      // Arrange
      ChainedSimulation rows = chainedSimulation();
      clearAmbientTenant();

      // Act & Assert
      mvc.perform(
              get(EXERCISE_URI + "/" + rows.simulation().getId() + "/export")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should export it as chained")
    void given_membershipOfItsTenant_should_exportItAsChained() throws Exception {
      // Arrange
      ChainedSimulation rows = chainedSimulationWithAStep();

      // Act
      Exported exported = export(rows.simulation());

      // Assert
      assertExportedAsChained(exported);
      assertTagRead(exported, rows.tag());
    }
  }

  @Nested
  @DisplayName("Routes")
  class Routes {

    private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

    /** The detail and the export of the simulation: the two reads that name its workflow. */
    private List<Integer> statusesOf(String simulationId, String pathTenant, String selector)
        throws Exception {
      String base =
          pathTenant == null ? EXERCISE_URI : TENANT_EXERCISE_URI.replace("{tenantId}", pathTenant);
      return List.of(
          statusOf(base + "/" + simulationId, selector),
          statusOf(base + "/" + simulationId + "/export", selector));
    }

    private int statusOf(String path, String selector) throws Exception {
      clearAmbientTenant();
      MockHttpServletRequestBuilder request = get(path).accept(MediaType.APPLICATION_JSON);
      if (selector != null) {
        request = request.header(TENANT_IDS_HEADER, selector);
      }
      return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private Tenant joinAnotherTenant() throws Exception {
      Tenant other = tenantHelper.createTenant("chained-simulation-member");
      tenantHelper.attachCurrentUserToTenant(other.getId());
      return other;
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given the tenant route of its tenant should reach the simulation")
    void given_tenantRouteOfItsTenant_should_reachTheSimulation() throws Exception {
      // Arrange
      joinAnotherTenant();
      ChainedSimulation rows = chainedSimulation();

      // Act
      List<Integer> statuses =
          statusesOf(rows.simulation().getId(), Tenant.DEFAULT_TENANT_UUID, null);

      // Assert
      assertThat(statuses).containsExactly(200, 200);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given the tenant route of another member tenant should not reach the simulation")
    void given_tenantRouteOfAnotherTenant_should_notReachTheSimulation() throws Exception {
      // Arrange
      Tenant other = joinAnotherTenant();
      ChainedSimulation rows = chainedSimulation();

      // Act
      List<Integer> statuses = statusesOf(rows.simulation().getId(), other.getId(), null);

      // Assert
      assertThat(statuses).containsExactly(404, 404);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a selector naming its tenant should reach the simulation")
    void given_selectorNamingItsTenant_should_reachTheSimulation() throws Exception {
      // Arrange
      joinAnotherTenant();
      ChainedSimulation rows = chainedSimulation();

      // Act
      List<Integer> statuses =
          statusesOf(rows.simulation().getId(), null, Tenant.DEFAULT_TENANT_UUID);

      // Assert
      assertThat(statuses).containsExactly(200, 200);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a selector naming another member tenant should not reach a default tenant simulation")
    void given_selectorNamingAnotherMemberTenant_should_notReachTheDefaultTenantSimulation()
        throws Exception {
      // Arrange
      Tenant other = joinAnotherTenant();
      ChainedSimulation rows = chainedSimulation();

      // Act
      List<Integer> statuses = statusesOf(rows.simulation().getId(), null, other.getId());

      // Assert
      assertThat(statuses).containsExactly(404, 404);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a selector naming a tenant the caller is not a member of should not reach it")
    void given_selectorNamingNonMemberTenant_should_notReachIt() throws Exception {
      // Arrange
      Tenant nonMember = tenantHelper.createTenant("chained-simulation-non-member");
      ChainedSimulation rows = chainedSimulation();

      // Act
      List<Integer> statuses = statusesOf(rows.simulation().getId(), null, nonMember.getId());

      // Assert: answered like a row that does not exist, with no hint that the id is there
      assertThat(statuses).containsExactly(404, 404);
    }
  }
}
