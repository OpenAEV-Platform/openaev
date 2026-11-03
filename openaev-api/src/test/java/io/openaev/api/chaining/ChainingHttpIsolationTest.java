package io.openaev.api.chaining;

import static io.openaev.api.chaining.ConditionApi.TENANT_CONDITION_URI;
import static io.openaev.api.chaining.StepApi.TENANT_STEP_URI;
import static io.openaev.api.chaining.WorkflowApi.TENANT_WORKFLOW_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.ConditionFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.ConditionComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * End-to-end proof that, with the chaining tables activated, the request scope alone isolates the
 * workflow, step and condition APIs between tenants.
 *
 * <p>Each tenant gets the same chaining graph: a simulation, its workflow template with three scope
 * rules (an endpoint, a team, an asset group of the same tenant), a step template referencing the
 * manual injector contract, and a condition linked to that step. The caller belongs to tenant B
 * only and addresses tenant A's rows through tenant B's path. Every read, update and delete below
 * resolves the row by its id alone, and the chaining tables carry no Hibernate filter, so nothing
 * but the statement inspector stands between the caller and tenant A's rows: emptying the armed
 * list below turns every cross-tenant assertion red while the same-tenant ones stay green.
 *
 * <p>The admin caller bypasses the parent-permission resolution of the access control aspect and
 * reaches the handlers' own reads; the non-admin caller is stopped by that resolution, which reads
 * the same tables. Both paths are covered. Writes are checked against raw JDBC on the test's own
 * connection, which the inspector never rewrites.
 *
 * <p>The three APIs are mapped on the tenant prefix only (pinned in {@link Routes}), so there is no
 * header route to cover; a header naming another tenant is ignored on the prefixed route.
 */
@Transactional
@TestPropertySource(
    properties =
        "openaev.tenant.active-tables=workflows,steps,conditions,scope_variables,steps_delay_queue,"
            + "workflow_scope_rules,workflow_states")
@WithMockUser(isAdmin = true)
@DisplayName("Chaining workflow, step and condition isolation through the real HTTP endpoints")
class ChainingHttpIsolationTest extends IntegrationTest {

  private static final String WORKFLOW_CONFIGURATION_URI =
      TENANT_WORKFLOW_URI + "/{workflowId}/configuration";
  private static final String WORKFLOW_INJECTOR_CONTRACT_URI =
      TENANT_WORKFLOW_URI + "/{workflowId}/injector_contracts/{injectorContractId}";
  private static final String STEP_BY_ID_URI = TENANT_STEP_URI + "/{stepId}";
  private static final String CONDITION_BY_ID_URI = TENANT_CONDITION_URI + "/{conditionId}";
  private static final String WORKFLOW_ID_PARAM = "workflow_id";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String CONDITION_NAME = "chaining-iso-condition";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ConditionComposer conditionComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private Graph graphA;
  private Graph graphB;

  /** The ids of one tenant's chaining graph. */
  private record Graph(
      String tenantId,
      String workflowId,
      String stepId,
      String conditionId,
      String endpointId,
      String teamId,
      String assetGroupId) {}

  @BeforeEach
  void seedOneChainingGraphPerTenant() throws Exception {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    exerciseComposer.reset();
    workflowComposer.reset();
    stepComposer.reset();
    conditionComposer.reset();
    endpointComposer.reset();
    teamComposer.reset();
    assetGroupComposer.reset();

    // Tenant A is foreign to the caller, who holds no membership in it.
    String tenantA = tenantHelper.createTenant("chaining-iso-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("chaining-iso-b").getId();
    graphA = seedGraph(tenantA);
    graphB = seedGraph(tenantB);
    // Seeded entities must leave the persistence context: a by-id read would otherwise be served
    // from it with no SQL for the inspector to scope, and a seeded step would be flushed again
    // under the request's scope.
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("given the seed, when reading raw rows, then each chaining row is in its own tenant")
  void given_seed_should_placeEveryChainingRowInItsOwnTenant() {
    // Arrange, Act, Assert
    for (Graph graph : List.of(graphA, graphB)) {
      assertEquals(graph.tenantId(), rawTenant("workflows", "workflow_id", graph.workflowId()));
      assertEquals(graph.tenantId(), rawTenant("steps", "step_id", graph.stepId()));
      assertEquals(graph.tenantId(), rawTenant("conditions", "condition_id", graph.conditionId()));
      assertEquals(
          3L,
          rawCount(
              "SELECT count(*) FROM workflow_scope_rules WHERE workflow_id = ? AND tenant_id = ?",
              graph.workflowId(),
              graph.tenantId()),
          "the three scope rules must sit in the workflow's tenant");
    }
  }

  @Nested
  @DisplayName("Workflow configuration")
  class WorkflowConfiguration {

    @Test
    @DisplayName("given B's own workflow, when reading its configuration, then it is returned")
    void given_ownWorkflow_should_returnItsConfiguration() throws Exception {
      // Act
      String response = perform(get(WORKFLOW_CONFIGURATION_URI, b(), graphB.workflowId()), 200);

      // Assert
      assertTrue(response.contains(graphB.endpointId()), "B's scope rules must be returned");
    }

    @Test
    @DisplayName("given A's workflow, when B reads its configuration, then it is not found")
    void given_otherTenantWorkflow_should_notFindItsConfiguration() throws Exception {
      // Act
      String response = perform(get(WORKFLOW_CONFIGURATION_URI, b(), graphA.workflowId()), 404);

      // Assert
      assertFalse(response.contains(graphA.endpointId()), "A's scope rules must not leak");
    }

    @Test
    @DisplayName("given B's own workflow, when updating its configuration, then it is changed")
    void given_ownWorkflow_should_updateItsConfiguration() throws Exception {
      // Act
      perform(
          put(WORKFLOW_CONFIGURATION_URI, b(), graphB.workflowId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(safeModeOff())),
          200);

      // Assert
      assertFalse(rawSafeMode(graphB.workflowId()), "B's own update must reach its row");
    }

    @Test
    @DisplayName(
        "given A's workflow, when B updates its configuration, then it is not found and unchanged")
    void given_otherTenantWorkflow_should_refuseTheUpdateAndLeaveItUnchanged() throws Exception {
      // Act
      perform(
          put(WORKFLOW_CONFIGURATION_URI, b(), graphA.workflowId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(safeModeOff())),
          404);

      // Assert
      assertTrue(rawSafeMode(graphA.workflowId()), "A's safe mode must be left on");
      assertEquals(
          3L,
          rawCount(
              "SELECT count(*) FROM workflow_scope_rules WHERE workflow_id = ?",
              graphA.workflowId()),
          "A's scope rules must be left in place");
    }
  }

  /** The six reads that resolve a workflow's scope rules into inventory rows. */
  static Stream<String> scopeReads() {
    return Stream.of(
        "valid-assets",
        "valid-teams",
        "scope/endpoints",
        "scope/endpoints/find",
        "scope/asset-groups",
        "scope/asset-groups/find");
  }

  @Nested
  @DisplayName("Workflow scope reads")
  class WorkflowScopeReads {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.api.chaining.ChainingHttpIsolationTest#scopeReads")
    @DisplayName("given B's own workflow, when resolving its scope, then B's target is returned")
    void given_ownWorkflow_should_resolveItsScopeTarget(String read) throws Exception {
      // Act
      String response = perform(scopeRead(read, graphB), 200);

      // Assert
      assertTrue(response.contains(targetOf(read, graphB)), "B's own target must be resolved");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.api.chaining.ChainingHttpIsolationTest#scopeReads")
    @DisplayName("given A's workflow, when B resolves its scope, then nothing is resolved")
    void given_otherTenantWorkflow_should_resolveNothing(String read) throws Exception {
      // Act
      String response = perform(scopeRead(read, graphA), 200);

      // Assert
      assertEquals("[]", response, "A's scope rules must resolve to nothing under B's scope");
    }

    private MockHttpServletRequestBuilder scopeRead(String read, Graph workflowOwner) {
      String uri = TENANT_WORKFLOW_URI + "/{workflowId}/" + read;
      if (!read.endsWith("/find")) {
        return get(uri, b(), workflowOwner.workflowId());
      }
      return post(uri, b(), workflowOwner.workflowId())
          .contentType(MediaType.APPLICATION_JSON)
          .content(asJsonString(List.of(targetOf(read, workflowOwner))));
    }

    private String targetOf(String read, Graph graph) {
      return switch (read) {
        case "valid-assets", "scope/endpoints", "scope/endpoints/find" -> graph.endpointId();
        case "valid-teams" -> graph.teamId();
        default -> graph.assetGroupId();
      };
    }
  }

  @Nested
  @DisplayName("Workflow injector contract")
  class WorkflowInjectorContract {

    @Test
    @DisplayName("given B's own workflow, when reading a contract its step uses, then it is found")
    void given_ownWorkflow_should_returnTheContractItsStepUses() throws Exception {
      // Act
      String response =
          perform(
              get(
                  WORKFLOW_INJECTOR_CONTRACT_URI,
                  b(),
                  graphB.workflowId(),
                  ManualContract.MANUAL_DEFAULT),
              200);

      // Assert
      assertTrue(response.contains(ManualContract.MANUAL_DEFAULT));
    }

    @Test
    @DisplayName("given A's workflow, when B reads a contract its step uses, then it is not found")
    void given_otherTenantWorkflow_should_notConfirmTheContractItsStepUses() throws Exception {
      // Act, Assert
      perform(
          get(
              WORKFLOW_INJECTOR_CONTRACT_URI,
              b(),
              graphA.workflowId(),
              ManualContract.MANUAL_DEFAULT),
          404);
    }
  }

  @Nested
  @DisplayName("Steps")
  class Steps {

    @Test
    @DisplayName("given B's own step, when reading it by id, then it is returned")
    void given_ownStep_should_returnIt() throws Exception {
      // Act
      String response = perform(get(STEP_BY_ID_URI, b(), graphB.stepId()), 200);

      // Assert
      assertEquals(graphB.stepId(), JsonPath.read(response, "$.step_id"));
    }

    @Test
    @DisplayName("given A's step, when B reads it by id, then it is not found")
    void given_otherTenantStep_should_notFindIt() throws Exception {
      // Act, Assert
      perform(get(STEP_BY_ID_URI, b(), graphA.stepId()), 404);
    }

    @Test
    @DisplayName("given B's own workflow, when listing its steps, then only B's step is listed")
    void given_ownWorkflow_should_listOnlyItsOwnStep() throws Exception {
      // Act
      String response =
          perform(get(TENANT_STEP_URI, b()).param(WORKFLOW_ID_PARAM, graphB.workflowId()), 200);

      // Assert
      assertTrue(response.contains(graphB.stepId()), "B's step must be listed");
      assertFalse(response.contains(graphA.stepId()), "A's step must not be listed");
    }

    @Test
    @DisplayName("given A's workflow, when B lists its steps, then nothing is listed")
    void given_otherTenantWorkflow_should_listNoStep() throws Exception {
      // Act
      String response =
          perform(get(TENANT_STEP_URI, b()).param(WORKFLOW_ID_PARAM, graphA.workflowId()), 200);

      // Assert
      assertEquals("[]", response, "A's steps must not be listed under B's scope");
    }

    @Test
    @DisplayName("given B's own workflow, when creating a step in it, then the step is B's")
    void given_ownWorkflow_should_createTheStepInB() throws Exception {
      // Act
      String response =
          perform(
              post(TENANT_STEP_URI, b())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(stepInput(graphB.workflowId(), null))),
              201);

      // Assert
      String createdId = JsonPath.read(response, "$.step_id");
      assertEquals(graphB.tenantId(), rawTenant("steps", "step_id", createdId));
    }

    @Test
    @DisplayName("given A's workflow, when B creates a step in it, then it is not found")
    void given_otherTenantWorkflow_should_refuseToCreateAStepInIt() throws Exception {
      // Act
      perform(
          post(TENANT_STEP_URI, b())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(stepInput(graphA.workflowId(), null))),
          404);

      // Assert
      assertEquals(1L, rawStepCount(graphA.workflowId()), "no step may be added to A's workflow");
    }

    @Test
    @DisplayName(
        "given A's condition, when B creates a step linked to it, then it is not found and no link"
            + " is made")
    void given_otherTenantCondition_should_refuseToLinkItToANewStep() throws Exception {
      // Act
      perform(
          post(TENANT_STEP_URI, b())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(stepInput(graphB.workflowId(), graphA.conditionId()))),
          404);

      // Assert
      // The step itself is saved before the link is refused. In production the 404 rolls the
      // request's own transaction back; here the request joins the test's transaction, so only the
      // link, which is never written, can be asserted.
      assertEquals(1L, rawLinkCount(graphA.conditionId()), "A's condition must keep its only link");
    }

    @Test
    @DisplayName("given B's own step, when updating it, then it is changed")
    void given_ownStep_should_updateIt() throws Exception {
      // Act
      perform(
          put(STEP_BY_ID_URI, b(), graphB.stepId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(stepInput(graphB.workflowId(), null))),
          200);

      // Assert
      assertTrue(
          rawStepData(graphB.stepId()).contains(STEP_TITLE), "B's own update must reach its row");
    }

    @Test
    @DisplayName("given A's step, when B updates it, then it is not found and unchanged")
    void given_otherTenantStep_should_refuseTheUpdateAndLeaveItUnchanged() throws Exception {
      // Act
      perform(
          put(STEP_BY_ID_URI, b(), graphA.stepId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(stepInput(graphA.workflowId(), null))),
          404);

      // Assert
      assertFalse(rawStepData(graphA.stepId()).contains(STEP_TITLE), "A's step must not change");
      assertEquals(1L, rawLinkCount(graphA.conditionId()), "A's step must keep its condition");
    }

    @Test
    @DisplayName("given B's own step, when deleting it, then it is gone")
    void given_ownStep_should_deleteIt() throws Exception {
      // Act
      perform(delete(STEP_BY_ID_URI, b(), graphB.stepId()), 204);

      // Assert
      assertEquals(0L, rawStepCount(graphB.workflowId()), "B's step must be deleted");
    }

    @Test
    @DisplayName("given A's step, when B deletes it, then it is not found and survives")
    void given_otherTenantStep_should_refuseTheDeleteAndSurvive() throws Exception {
      // Act
      perform(delete(STEP_BY_ID_URI, b(), graphA.stepId()), 404);

      // Assert
      assertEquals(1L, rawStepCount(graphA.workflowId()), "A's step must survive");
      assertEquals(1L, rawLinkCount(graphA.conditionId()), "A's step must keep its condition");
    }
  }

  @Nested
  @DisplayName("Conditions")
  class Conditions {

    @Test
    @DisplayName("given B's own condition, when reading it by id, then it is returned")
    void given_ownCondition_should_returnIt() throws Exception {
      // Act
      String response = perform(get(CONDITION_BY_ID_URI, b(), graphB.conditionId()), 200);

      // Assert
      assertEquals(graphB.conditionId(), JsonPath.read(response, "$.event_id"));
    }

    @Test
    @DisplayName("given A's condition, when B reads it by id, then it is not found")
    void given_otherTenantCondition_should_notFindIt() throws Exception {
      // Act, Assert
      perform(get(CONDITION_BY_ID_URI, b(), graphA.conditionId()), 404);
    }

    @Test
    @DisplayName(
        "given B's own workflow, when listing its conditions, then only B's condition is listed")
    void given_ownWorkflow_should_listOnlyItsOwnCondition() throws Exception {
      // Act
      String response =
          perform(
              get(TENANT_CONDITION_URI, b()).param(WORKFLOW_ID_PARAM, graphB.workflowId()), 200);

      // Assert
      assertTrue(response.contains(graphB.conditionId()), "B's condition must be listed");
      assertFalse(response.contains(graphA.conditionId()), "A's condition must not be listed");
    }

    @Test
    @DisplayName("given A's workflow, when B lists its conditions, then nothing is listed")
    void given_otherTenantWorkflow_should_listNoCondition() throws Exception {
      // Act
      String response =
          perform(
              get(TENANT_CONDITION_URI, b()).param(WORKFLOW_ID_PARAM, graphA.workflowId()), 200);

      // Assert
      assertEquals("[]", response, "A's conditions must not be listed under B's scope");
    }

    @Test
    @DisplayName(
        "given B's own workflow, when creating a condition in it, then the condition is B's")
    void given_ownWorkflow_should_createTheConditionInB() throws Exception {
      // Act
      String response =
          perform(
              post(TENANT_CONDITION_URI, b())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(eventInput(graphB.workflowId(), null))),
              201);

      // Assert
      String createdId = JsonPath.read(response, "$.event_id");
      assertEquals(graphB.tenantId(), rawTenant("conditions", "condition_id", createdId));
    }

    @Test
    @DisplayName("given A's workflow, when B creates a condition in it, then it is not found")
    void given_otherTenantWorkflow_should_refuseToCreateAConditionInIt() throws Exception {
      // Act
      perform(
          post(TENANT_CONDITION_URI, b())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(eventInput(graphA.workflowId(), null))),
          404);

      // Assert
      assertEquals(
          1L, rawConditionCount(graphA.workflowId()), "no condition may be added to A's workflow");
    }

    @Test
    @DisplayName(
        "given A's step, when B creates a condition linked to it, then it is not found and no link"
            + " is made")
    void given_otherTenantStep_should_refuseToLinkItToANewCondition() throws Exception {
      // Act
      perform(
          post(TENANT_CONDITION_URI, b())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(eventInput(graphB.workflowId(), graphA.stepId()))),
          404);

      // Assert
      assertEquals(
          1L,
          rawCount("SELECT count(*) FROM conditions_steps WHERE step_id = ?", graphA.stepId()),
          "A's step must keep its only condition");
    }

    @Test
    @DisplayName("given B's own condition, when updating it, then it is changed")
    void given_ownCondition_should_updateIt() throws Exception {
      // Act
      perform(
          put(CONDITION_BY_ID_URI, b(), graphB.conditionId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(eventInput(graphB.workflowId(), null))),
          200);

      // Assert
      assertEquals(UPDATED_CONDITION_NAME, rawConditionName(graphB.conditionId()));
    }

    @Test
    @DisplayName("given A's condition, when B updates it, then it is not found and unchanged")
    void given_otherTenantCondition_should_refuseTheUpdateAndLeaveItUnchanged() throws Exception {
      // Act
      perform(
          put(CONDITION_BY_ID_URI, b(), graphA.conditionId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(eventInput(graphA.workflowId(), null))),
          404);

      // Assert
      assertEquals(CONDITION_NAME, rawConditionName(graphA.conditionId()));
    }

    @Test
    @DisplayName("given B's own condition, when deleting it, then it is gone")
    void given_ownCondition_should_deleteIt() throws Exception {
      // Act
      perform(delete(CONDITION_BY_ID_URI, b(), graphB.conditionId()), 204);

      // Assert
      assertEquals(0L, rawConditionCount(graphB.workflowId()), "B's condition must be deleted");
    }

    @Test
    @DisplayName("given A's condition, when B deletes it, then it is not found and survives")
    void given_otherTenantCondition_should_refuseTheDeleteAndSurvive() throws Exception {
      // Act
      perform(delete(CONDITION_BY_ID_URI, b(), graphA.conditionId()), 404);

      // Assert
      assertEquals(1L, rawConditionCount(graphA.workflowId()), "A's condition must survive");
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("Parent resolution of a non-admin caller of tenant B")
  class NonAdminParentResolution {

    @BeforeEach
    void grantAssessmentCapabilitiesInB() {
      tenantHelper.grantCapabilitiesInTenant(
          graphB.tenantId(),
          Set.of(
              Capability.ACCESS_ASSESSMENT,
              Capability.MANAGE_ASSESSMENT,
              Capability.DELETE_ASSESSMENT));
    }

    @Test
    @DisplayName("given B's own step, when a B user reads it, then the capabilities suffice")
    void given_ownStep_should_beReadableWithTenantCapabilities() throws Exception {
      // Act, Assert
      perform(get(STEP_BY_ID_URI, b(), graphB.stepId()), 200);
    }

    @Test
    @DisplayName("given A's workflow, when a B user reads its configuration, then it is not found")
    void given_otherTenantWorkflow_should_notResolveItsSimulation() throws Exception {
      // Act, Assert
      perform(get(WORKFLOW_CONFIGURATION_URI, b(), graphA.workflowId()), 404);
    }

    @Test
    @DisplayName("given A's workflow, when a B user resolves its scope, then it is not found")
    void given_otherTenantWorkflow_should_notResolveItsScope() throws Exception {
      // Act, Assert
      perform(
          get(TENANT_WORKFLOW_URI + "/{workflowId}/valid-assets", b(), graphA.workflowId()), 404);
    }

    @Test
    @DisplayName("given A's workflow, when a B user lists its steps, then it is not found")
    void given_otherTenantWorkflow_should_notListItsSteps() throws Exception {
      // Act, Assert
      perform(get(TENANT_STEP_URI, b()).param(WORKFLOW_ID_PARAM, graphA.workflowId()), 404);
    }

    @Test
    @DisplayName("given A's step, when a B user updates it, then it is not found and unchanged")
    void given_otherTenantStep_should_notResolveItsWorkflow() throws Exception {
      // Act
      perform(
          put(STEP_BY_ID_URI, b(), graphA.stepId())
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(stepInput(graphA.workflowId(), null))),
          404);

      // Assert
      assertFalse(rawStepData(graphA.stepId()).contains(STEP_TITLE), "A's step must not change");
    }

    @Test
    @DisplayName("given A's condition, when a B user deletes it, then it is not found and survives")
    void given_otherTenantCondition_should_notResolveItsWorkflow() throws Exception {
      // Act
      perform(delete(CONDITION_BY_ID_URI, b(), graphA.conditionId()), 404);

      // Assert
      assertEquals(1L, rawConditionCount(graphA.workflowId()), "A's condition must survive");
    }
  }

  @Nested
  @DisplayName("Routes")
  class Routes {

    @Test
    @DisplayName(
        "given the three chaining APIs, when listing their mappings, then every route carries the"
            + " tenant prefix")
    void given_chainingApis_should_mapNoRouteOutsideTheTenantPrefix() {
      // Arrange
      Set<Class<?>> chainingApis = Set.of(WorkflowApi.class, StepApi.class, ConditionApi.class);

      // Act
      List<String> patterns =
          handlerMapping.getHandlerMethods().entrySet().stream()
              .filter(entry -> chainingApis.contains(entry.getValue().getBeanType()))
              .map(Map.Entry::getKey)
              .map(RequestMappingInfo::getPatternValues)
              .flatMap(Set::stream)
              .toList();

      // Assert
      // Nine workflow routes, five step routes and five condition routes: a new one must come
      // with its isolation cases here, and a route outside the prefix with its header route case.
      assertThat(patterns).hasSize(19).allMatch(pattern -> pattern.startsWith(TENANT_PREFIX + "/"));
    }

    @Test
    @DisplayName(
        "given a member of both tenants on A's path, when reading A's step, then it is found")
    void given_memberOfBothTenantsOnTenantAPath_should_readTenantAStep() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(graphA.tenantId());

      // Act, Assert
      perform(get(STEP_BY_ID_URI, graphA.tenantId(), graphA.stepId()), 200);
    }

    @Test
    @DisplayName(
        "given a member of both tenants on B's path with a header naming A, when reading A's step,"
            + " then the path wins and it is not found")
    void given_tenantBPathWithHeaderNamingTenantA_should_keepTheTenantBScope() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(graphA.tenantId());

      // Act, Assert
      perform(
          get(STEP_BY_ID_URI, b(), graphA.stepId()).header(TENANT_IDS_HEADER, graphA.tenantId()),
          404);
    }
  }

  // -- Seed --

  private Graph seedGraph(String tenantId) {
    Tenant tenant = new Tenant(tenantId);

    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setTenant(tenant);
    endpointComposer.forEndpoint(endpoint).persist();
    Team team = TeamFixture.getDefaultTeam();
    team.setTenant(tenant);
    teamComposer.forTeam(team).persist();
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup("chaining-iso-group");
    assetGroup.setTenant(tenant);
    assetGroupComposer.forAssetGroup(assetGroup).persist();

    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(tenant);
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setSafeModeEnabled(true);
    workflow
        .getWorkflowScopeRules()
        .addAll(
            List.of(
                scopeRule(
                    workflow, ScopeRuleSource.ASSET, ScopeRuleValueType.ASSET_ID, endpoint.getId()),
                scopeRule(workflow, ScopeRuleSource.TEAM, ScopeRuleValueType.TEAM_ID, team.getId()),
                scopeRule(
                    workflow,
                    ScopeRuleSource.ASSET_GROUP,
                    ScopeRuleValueType.ASSET_GROUP_ID,
                    assetGroup.getId())));
    Step step = StepFixture.getDefaultStepTemplate();
    step.setData("{\"inject_injector_contract\": \"" + ManualContract.MANUAL_DEFAULT + "\"}");
    StepComposer.Composer stepGraph = stepComposer.forStep(step);
    workflowComposer
        .forWorkflow(workflow)
        .withSimulation(exerciseComposer.forExercise(exercise))
        .withStep(stepGraph)
        .persist();
    stepGraph.persist();

    Condition condition = ConditionFixture.getDefaultCondition(PrimitiveType.Port, "445");
    condition.setWorkflowId(workflow.getId());
    condition.setName(CONDITION_NAME);
    conditionComposer.forCondition(condition).withStep(stepGraph).persist();

    return new Graph(
        tenantId,
        workflow.getId(),
        step.getId(),
        condition.getId(),
        endpoint.getId(),
        team.getId(),
        assetGroup.getId());
  }

  private static WorkflowScopeRule scopeRule(
      Workflow workflow, ScopeRuleSource source, ScopeRuleValueType valueType, String targetId) {
    return WorkflowScopeRule.builder()
        .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
        .ruleSource(source)
        .valueType(valueType)
        .ruleValue(targetId)
        .workflow(workflow)
        .build();
  }

  // -- Request payloads --

  private static final String STEP_TITLE = "chaining-iso-updated-step";
  private static final String UPDATED_CONDITION_NAME = "chaining-iso-updated-condition";

  private static StepInput stepInput(String workflowId, String linkedConditionId) {
    InjectInput inject = new InjectInput();
    inject.setTitle(STEP_TITLE);
    inject.setInjectorContract(ManualContract.MANUAL_DEFAULT);
    StepInput input = new StepInput();
    input.setWorkflowId(workflowId);
    input.setStepAction(StepActionClass.INJECT_EXECUTION);
    input.setDataStep(inject);
    input.setConditions(List.of());
    if (linkedConditionId != null) {
      input.setConditionIds(List.of(linkedConditionId));
    }
    return input;
  }

  private static EventInput eventInput(String workflowId, String linkedStepId) {
    ConditionCreateInput root = new ConditionCreateInput();
    root.setTemporaryId("chaining-iso-root");
    root.setType(ConditionType.AND);
    return EventInput.builder()
        .name(UPDATED_CONDITION_NAME)
        .workflowId(workflowId)
        .conditions(List.of(root))
        .stepIds(linkedStepId != null ? List.of(linkedStepId) : null)
        .build();
  }

  private static WorkflowConfigurationInput safeModeOff() {
    return WorkflowConfigurationInput.builder()
        .rateLimitEnabled(false)
        .safeModeEnabled(false)
        .build();
  }

  // -- Requests --

  private String b() {
    return graphB.tenantId();
  }

  private String perform(MockHttpServletRequestBuilder request, int expectedStatus)
      throws Exception {
    String response =
        mvc.perform(request.with(csrf()))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    entityManager.flush();
    return response;
  }

  // -- Ground truth, raw JDBC: the inspector never rewrites a statement it did not generate --

  private String rawTenant(String table, String idColumn, String id) {
    return rawString("SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?", id);
  }

  private boolean rawSafeMode(String workflowId) {
    return Boolean.parseBoolean(
        rawString(
            "SELECT workflow_safe_mode_enabled::text FROM workflows WHERE workflow_id = ?",
            workflowId));
  }

  private String rawStepData(String stepId) {
    return rawString("SELECT step_data::text FROM steps WHERE step_id = ?", stepId);
  }

  private String rawConditionName(String conditionId) {
    return rawString("SELECT condition_name FROM conditions WHERE condition_id = ?", conditionId);
  }

  private long rawStepCount(String workflowId) {
    return rawCount("SELECT count(*) FROM steps WHERE step_workflow_id = ?", workflowId);
  }

  private long rawConditionCount(String workflowId) {
    return rawCount("SELECT count(*) FROM conditions WHERE condition_workflow_id = ?", workflowId);
  }

  private long rawLinkCount(String conditionId) {
    return rawCount("SELECT count(*) FROM conditions_steps WHERE condition_id = ?", conditionId);
  }

  private long rawCount(String sql, String... parameters) {
    return Long.parseLong(rawString(sql.replace("count(*)", "count(*)::text"), parameters));
  }

  private String rawString(String sql, String... parameters) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
