package io.openaev.api.attack_path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Attack Path API integration tests")
class AttackPathApiTest extends IntegrationTest {

  private static final String TENANT_ATTACK_PATH_URI =
      "/api/tenants/{tenantId}/exercises/{exerciseId}/attack-path";

  @Autowired private MockMvc mockMvc;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private CacheManager cacheManager;
  @Autowired private ObjectMapper objectMapper;

  private String originalDevFeatures;

  @BeforeEach
  void enableFeature() {
    originalDevFeatures = openAEVConfig.getEnabledDevFeatures();
    openAEVConfig.setEnabledDevFeatures(PreviewFeature.CHAINING_ATTACK_PATH.getValue());
    clearFeatureCache();
  }

  @AfterEach
  void restoreFeature() {
    openAEVConfig.setEnabledDevFeatures(originalDevFeatures);
    clearFeatureCache();
  }

  private void clearFeatureCache() {
    var cache = cacheManager.getCache("global");
    if (cache != null) {
      cache.clear();
    }
  }

  private String attackPathUri(String exerciseId) {
    return tenantUri(TENANT_ATTACK_PATH_URI.replace("{exerciseId}", exerciseId));
  }

  // -- TESTS --

  @Nested
  @DisplayName("Given a simulation with a chaining workflow")
  class WithWorkflow {

    @Test
    @DisplayName("should return attack path graph with nodes, edges, and stats")
    void given_simulationWithWorkflow_should_returnAttackPathGraph() throws Exception {
      // Arrange
      Exercise exercise = ExerciseFixture.createRunningAttackExercise();
      ExerciseComposer.Composer exerciseComp = exerciseComposer.forExercise(exercise).persist();

      Endpoint endpoint = EndpointFixture.createEndpoint();
      EndpointComposer.Composer endpointComp = endpointComposer.forEndpoint(endpoint).persist();

      Inject inject = InjectFixture.getDefaultInject();
      inject.setExercise(exercise);
      InjectComposer.Composer injectComp = injectComposer.forInject(inject).persist();

      Step step = StepFixture.getDefaultStepExecution(StepStatus.RUN);
      step.setData("{\"inject_id\": \"" + inject.getId() + "\", \"inject_title\": \"Mimikatz\"}");

      Workflow workflow = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
      workflowComposer
          .forWorkflow(workflow)
          .withSimulation(exerciseComp)
          .withStep(stepComposer.forStep(step))
          .persist();

      InjectExpectation prevention =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              InjectExpectation.EXPECTATION_TYPE.PREVENTION,
              InjectExpectation.EXPECTATION_STATUS.SUCCESS);
      prevention.setExercise(exercise);
      prevention.setInject(inject);
      prevention.setAsset(endpoint);
      injectExpectationComposer.forExpectation(prevention).persist();

      // Act
      String response =
          mockMvc
              .perform(get(attackPathUri(exercise.getId())).with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      JsonNode body = objectMapper.readTree(response);
      JsonNode nodes = body.get("attack_path_nodes");
      JsonNode edges = body.get("attack_path_edges");
      JsonNode stats = body.get("attack_path_stats");

      Assertions.assertNotNull(nodes);
      Assertions.assertTrue(nodes.isArray());
      Assertions.assertTrue(nodes.size() >= 1, "Should have at least one ACTION node");

      // Verify ACTION node
      JsonNode actionNode = findNodeByType(nodes, "ACTION");
      Assertions.assertNotNull(actionNode, "Should contain an ACTION node");
      Assertions.assertEquals("Mimikatz", actionNode.get("node_label").asText());
      Assertions.assertEquals("prevented", actionNode.get("node_status").asText());

      // Verify ASSET node (linked via expectation)
      JsonNode assetNode = findNodeByType(nodes, "ASSET");
      Assertions.assertNotNull(assetNode, "Should contain an ASSET node");
      Assertions.assertEquals(
          EndpointFixture.WINDOWS_HOSTNAME, assetNode.get("node_hostname").asText());

      // Verify edges
      Assertions.assertNotNull(edges);
      Assertions.assertTrue(edges.isArray());

      // Verify stats
      Assertions.assertNotNull(stats);
      Assertions.assertEquals(1, stats.get("stats_prevented").asInt());
      Assertions.assertEquals(0, stats.get("stats_undetected").asInt());
      Assertions.assertEquals(1, stats.get("stats_total_actions").asInt());
      Assertions.assertEquals(1, stats.get("stats_executed_actions").asInt());
    }

    @Test
    @DisplayName("should resolve status as detected when PREVENTION fails and DETECTION succeeds")
    void given_simulationWithExpectations_should_resolveStatusColors() throws Exception {
      // Arrange
      Exercise exercise = ExerciseFixture.createRunningAttackExercise();
      ExerciseComposer.Composer exerciseComp = exerciseComposer.forExercise(exercise).persist();

      Inject inject = InjectFixture.getDefaultInject();
      inject.setExercise(exercise);
      injectComposer.forInject(inject).persist();

      Step step = StepFixture.getDefaultStepExecution(StepStatus.RUN);
      step.setData("{\"inject_id\": \"" + inject.getId() + "\", \"inject_title\": \"Kerberoast\"}");

      Workflow workflow = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
      workflowComposer
          .forWorkflow(workflow)
          .withSimulation(exerciseComp)
          .withStep(stepComposer.forStep(step))
          .persist();

      // PREVENTION failed
      InjectExpectation prevention =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              InjectExpectation.EXPECTATION_TYPE.PREVENTION,
              InjectExpectation.EXPECTATION_STATUS.FAILED);
      prevention.setExercise(exercise);
      prevention.setInject(inject);
      injectExpectationComposer.forExpectation(prevention).persist();

      // DETECTION succeeded
      InjectExpectation detection =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              InjectExpectation.EXPECTATION_TYPE.DETECTION,
              InjectExpectation.EXPECTATION_STATUS.SUCCESS);
      detection.setExercise(exercise);
      detection.setInject(inject);
      injectExpectationComposer.forExpectation(detection).persist();

      // Act
      String response =
          mockMvc
              .perform(get(attackPathUri(exercise.getId())).with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      JsonNode body = objectMapper.readTree(response);
      JsonNode actionNode = findNodeByType(body.get("attack_path_nodes"), "ACTION");
      Assertions.assertNotNull(actionNode);
      Assertions.assertEquals("detected", actionNode.get("node_status").asText());

      JsonNode stats = body.get("attack_path_stats");
      Assertions.assertEquals(0, stats.get("stats_prevented").asInt());
      Assertions.assertEquals(1, stats.get("stats_detected").asInt());
    }

    @Test
    @DisplayName("should return empty graph when no workflow is in RUN status")
    void given_simulationWithNoRunningWorkflow_should_returnEmptyGraph() throws Exception {
      // Arrange
      Exercise exercise = ExerciseFixture.createRunningAttackExercise();
      exerciseComposer.forExercise(exercise).persist();

      // Act
      String response =
          mockMvc
              .perform(get(attackPathUri(exercise.getId())).with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      JsonNode body = objectMapper.readTree(response);
      Assertions.assertEquals(0, body.get("attack_path_nodes").size());
      Assertions.assertEquals(0, body.get("attack_path_edges").size());
      Assertions.assertEquals(0, body.get("attack_path_stats").get("stats_total_actions").asInt());
    }
  }

  @Nested
  @DisplayName("Security & access control")
  class Security {

    @Test
    @DisplayName("should return 404 when feature flag is disabled")
    void given_featureFlagDisabled_should_return404() throws Exception {
      // Arrange
      openAEVConfig.setEnabledDevFeatures("");
      clearFeatureCache();

      Exercise exercise = ExerciseFixture.createRunningAttackExercise();
      exerciseComposer.forExercise(exercise).persist();

      // Act & Assert
      mockMvc
          .perform(get(attackPathUri(exercise.getId())).with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return 404 when exercise does not exist")
    void given_nonExistentExercise_should_return404() throws Exception {
      // Arrange
      String fakeExerciseId = UUID.randomUUID().toString();

      // Act & Assert
      mockMvc
          .perform(get(attackPathUri(fakeExerciseId)).with(csrf()))
          .andExpect(status().isNotFound());
    }
  }

  // -- Helpers --

  private JsonNode findNodeByType(JsonNode nodes, String type) {
    for (JsonNode node : nodes) {
      if (type.equals(node.get("node_type").asText())) {
        return node;
      }
    }
    return null;
  }
}
