package io.openaev.api.chaining;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.WorkflowFixture.getDefaultWorkflowScopeRuleInputList;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Workflow API integration tests")
class WorkflowApiTest extends IntegrationTest {

  private static final String WORKFLOW_URI = "/api/workflows";

  @Autowired private MockMvc mockMvc;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private CacheManager cacheManager;

  private String originalDevFeatures;

  @BeforeEach
  void enableChainingFeature() {
    originalDevFeatures = openAEVConfig.getEnabledDevFeatures();
    openAEVConfig.setEnabledDevFeatures(PreviewFeature.INJECT_CHAINING.getValue());
    clearFeatureCache();
  }

  @AfterEach
  void restoreDevFeatures() {
    openAEVConfig.setEnabledDevFeatures(originalDevFeatures);
    clearFeatureCache();
  }

  private void clearFeatureCache() {
    var cache = cacheManager.getCache("global");
    if (cache != null) {
      cache.clear();
    }
  }

  @Test
  @DisplayName("Fetch Workflow Configuration should return configuration for a template workflow")
  void getWorkflowConfiguration_shouldReturnConfiguration() throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    setWorkflowConfiguration(workflow, true, 3, 10, true, 3660);

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(
                get(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration").with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    JsonNode body = new ObjectMapper().readTree(response);
    assertTrue(body.get("workflow_configuration_rate_limit_enabled").asBoolean());
    assertEquals(3, body.get("workflow_configuration_max_attempts").asInt());
    assertEquals(10, body.get("workflow_configuration_max_temporal_rate_seconds").asInt());
    assertTrue(body.get("workflow_configuration_timeout_enabled").asBoolean());
    assertEquals(3660, body.get("workflow_configuration_timeout_seconds").asLong());
    assertTrue(body.get("workflow_configuration_safe_mode_enabled").asBoolean());
  }

  @Test
  @DisplayName("Fetch Workflow Configuration should return 404 when workflow does not exist")
  void getWorkflowConfiguration_shouldReturnNotFoundWhenWorkflowMissing() throws Exception {
    // -- PREPARE --
    String workflowId = "missing-workflow-id";

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(get(WORKFLOW_URI + "/" + workflowId + "/workflow-configuration").with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(
        "Element not found: Workflow TEMPLATE not found. Workflow ID : " + workflowId,
        JsonPath.read(response, "$.message"));
  }

  @Test
  @DisplayName(
      "Fetch Workflow Configuration should return 404 when INJECT_CHAINING feature is disabled")
  void getWorkflowConfiguration_shouldReturnNotFoundWhenFeatureDisabled() throws Exception {
    // -- PREPARE --
    openAEVConfig.setEnabledDevFeatures("");
    clearFeatureCache();
    Workflow workflow = createTemplateWorkflow();

    // -- EXECUTE & ASSERT --
    String response =
        mockMvc
            .perform(
                get(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration").with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(
        "Element not found: INJECT_CHAINING feature is not enabled",
        JsonPath.read(response, "$.message"));
  }

  @Test
  @DisplayName(
      "Update Workflow Configuration should return 404 when INJECT_CHAINING feature is disabled")
  void updateWorkflowConfiguration_shouldReturnNotFoundWhenFeatureDisabled() throws Exception {
    // -- PREPARE --
    openAEVConfig.setEnabledDevFeatures("");
    clearFeatureCache();
    Workflow workflow = createTemplateWorkflow();
    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder().rateLimitEnabled(false).safeModeEnabled(true).build();

    // -- EXECUTE & ASSERT --
    String response =
        mockMvc
            .perform(
                put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(
        "Element not found: INJECT_CHAINING feature is not enabled",
        JsonPath.read(response, "$.message"));
  }

  @Test
  @DisplayName("Update Workflow Configuration should update and persist inline configuration")
  void updateWorkflowConfiguration_shouldUpdateAndPersistConfiguration() throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();

    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(7)
            .maxTemporalRateSeconds(15L)
            .timeoutEnabled(true)
            .timeoutSeconds(5400L) // 1 h 30 min
            .safeModeEnabled(false)
            .build();

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(
                put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT RESPONSE --
    JsonNode body = new ObjectMapper().readTree(response);
    assertTrue(body.get("workflow_configuration_rate_limit_enabled").asBoolean());
    assertEquals(7, body.get("workflow_configuration_max_attempts").asInt());
    assertEquals(15, body.get("workflow_configuration_max_temporal_rate_seconds").asInt());
    assertTrue(body.get("workflow_configuration_timeout_enabled").asBoolean());
    assertEquals(5400L, body.get("workflow_configuration_timeout_seconds").asLong());
    assertFalse(body.get("workflow_configuration_safe_mode_enabled").asBoolean());

    // -- ASSERT DATABASE (config stored inline on the workflow row) --
    Workflow saved = workflowRepository.findById(workflow.getId()).orElseThrow();
    assertTrue(saved.isRateLimitEnabled());
    assertEquals(7, saved.getMaxAttempts());
    assertEquals(15L, saved.getMaxTemporalRateSeconds());
    assertTrue(saved.isTimeoutEnabled());
    assertEquals(5400L, saved.getTimeoutSeconds());
    assertFalse(saved.isSafeModeEnabled());
  }

  @Test
  @DisplayName(
      "Update Workflow Configuration should return 400 when rate limit max attempts is below minimum")
  void updateWorkflowConfiguration_shouldReturnBadRequestWhenMaxAttemptsBelowMin()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(0) // below @Min(1)
            .maxTemporalRateSeconds(10L)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Update Workflow Configuration should return 400 when rate limit max attempts exceeds maximum")
  void updateWorkflowConfiguration_shouldReturnBadRequestWhenMaxAttemptsAboveMax()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(100) // above @Max(99)
            .maxTemporalRateSeconds(10L)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Update Workflow Configuration should return 400 when max temporal rate seconds is below minimum")
  void updateWorkflowConfiguration_shouldReturnBadRequestWhenMaxTemporalRateSecondsBelowMin()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(3)
            .maxTemporalRateSeconds(0L) // below @Min(1)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Update Workflow Configuration should return 400 when max temporal rate seconds exceeds maximum")
  void updateWorkflowConfiguration_shouldReturnBadRequestWhenMaxTemporalRateSecondsAboveMax()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(3)
            .maxTemporalRateSeconds(6000L) // above @Max(5940)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Update Workflow Configuration should return 400 when timeout seconds exceed maximum")
  void updateWorkflowConfiguration_shouldReturnBadRequestWhenTimeoutSecondsAboveMax()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder()
            .timeoutEnabled(true)
            .timeoutSeconds(86401L) // above @Max(86400)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Update Workflow Configuration should return 400 when timeout seconds are negative")
  void updateWorkflowConfiguration_shouldReturnBadRequestWhenTimeoutSecondsNegative()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder()
            .timeoutEnabled(true)
            .timeoutSeconds(-1L) // below @Min(60)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Update workflow configuration should persist scope rules with expected value types")
  void updateWorkflowConfiguration_shouldPersistScopeRulesWithExpectedValueTypes()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();

    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(7)
            .maxTemporalRateSeconds(15L)
            .timeoutEnabled(true)
            .timeoutSeconds(5400L)
            .safeModeEnabled(false)
            .workflowScopeRules(getDefaultWorkflowScopeRuleInputList())
            .build();

    // -- EXECUTE --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/workflow-configuration")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // -- ASSERT DATABASE --
    Workflow saved = workflowRepository.findById(workflow.getId()).orElseThrow();
    List<WorkflowScopeRule> rules = saved.getWorkflowScopeRules();

    assertEquals(5, rules.size());
    assertEquals(3, saved.getWhitelist().size());
    assertEquals(2, saved.getBlacklist().size());

    WorkflowScopeRule ipRule =
        saved.getWhitelist().stream()
            .filter(r -> "10.10.10.10".equals(r.getRuleValue()))
            .findFirst()
            .orElseThrow();
    assertEquals(ScopeRuleValueType.IP, ipRule.getValueType());
    assertEquals(ScopeRuleSelectedMode.WHITELIST, ipRule.getSelectedMode());
    assertSame(saved, ipRule.getWorkflow());

    WorkflowScopeRule domainRule =
        saved.getWhitelist().stream()
            .filter(r -> "example.org".equals(r.getRuleValue()))
            .findFirst()
            .orElseThrow();
    assertEquals(ScopeRuleValueType.DOMAIN, domainRule.getValueType());

    WorkflowScopeRule assetRule =
        saved.getWhitelist().stream()
            .filter(r -> "asset-123".equals(r.getRuleValue()))
            .findFirst()
            .orElseThrow();
    assertEquals(ScopeRuleValueType.ASSET_ID, assetRule.getValueType());

    WorkflowScopeRule subnetRule =
        saved.getBlacklist().stream()
            .filter(r -> "10.10.10.0/24".equals(r.getRuleValue()))
            .findFirst()
            .orElseThrow();
    assertEquals(ScopeRuleValueType.IP_SUBNET, subnetRule.getValueType());
    assertEquals(ScopeRuleSelectedMode.BLACKLIST, subnetRule.getSelectedMode());

    WorkflowScopeRule assetGroupRule =
        saved.getBlacklist().stream()
            .filter(r -> "asset-group-1".equals(r.getRuleValue()))
            .findFirst()
            .orElseThrow();
    assertEquals(ScopeRuleValueType.ASSET_GROUP_ID, assetGroupRule.getValueType());
  }

  // -- Helpers --

  private Workflow createTemplateWorkflow() {
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setStatus(WorkflowStatus.TEMPLATE);
    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setFrom("exercise@mail.fr");

    return workflowComposer
        .forWorkflow(workflow)
        .withSimulation(exerciseComposer.forExercise(exercise))
        .persist()
        .get();
  }

  /**
   * Applies configuration values directly onto the workflow row and saves. Returns the updated
   * workflow loaded from the repository so callers can assert database state.
   */
  private Workflow setWorkflowConfiguration(
      Workflow workflow,
      boolean rateLimitEnabled,
      int maxAttempts,
      int maxTemporalRateSeconds,
      boolean timeoutEnabled,
      int timeoutSeconds) {
    workflow.setRateLimitEnabled(rateLimitEnabled);
    workflow.setMaxAttempts(maxAttempts);
    workflow.setMaxTemporalRateSeconds((long) maxTemporalRateSeconds);
    workflow.setTimeoutEnabled(timeoutEnabled);
    workflow.setTimeoutSeconds((long) timeoutSeconds);
    workflow.setSafeModeEnabled(true);
    workflowRepository.save(workflow);
    return workflowRepository.findById(workflow.getId()).orElseThrow();
  }
}
