package io.openaev.api.chaining;

import static io.openaev.api.chaining.WorkflowApi.WORKFLOW_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.database.repository.ChainingConfigurationRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
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
@DisplayName("Chaining API integration tests")
class WorkflowApiTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ChainingConfigurationRepository chainingConfigurationRepository;
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
  @DisplayName("Fetch Chaining Configuration should return configuration for a template workflow")
  void getChainingConfiguration_shouldReturnConfiguration() throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    attachChainingConfiguration(workflow, true, 3, 10, true, 3660);

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(get(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    JsonNode body = new ObjectMapper().readTree(response);

    assertTrue(body.get("chaining_configuration_rate_limit_enabled").asBoolean());
    assertEquals(3, body.get("chaining_configuration_max_attempts").asInt());
    assertEquals(10, body.get("chaining_configuration_max_temporal_rate_seconds").asInt());
    assertTrue(body.get("chaining_configuration_timeout_enabled").asBoolean());
    assertEquals(3660, body.get("chaining_configuration_timeout_seconds").asLong());
    assertTrue(body.get("chaining_configuration_safe_mode_enabled").asBoolean());
  }

  @Test
  @DisplayName("Fetch Chaining Configuration should return 404 when workflow does not exist")
  void getChainingConfiguration_shouldReturnNotFoundWhenWorkflowMissing() throws Exception {
    // -- PREPARE --
    String workflowId = "missing-workflow-id";

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(get(WORKFLOW_URI + "/" + workflowId + "/chaining-configuration"))
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
      "Fetch Chaining Configuration should return 404 when INJECT_CHAINING feature is disabled")
  void getChainingConfiguration_shouldReturnNotFoundWhenFeatureDisabled() throws Exception {
    // -- PREPARE --
    openAEVConfig.setEnabledDevFeatures("");
    clearFeatureCache();
    Workflow workflow = createTemplateWorkflow();

    // -- EXECUTE & ASSERT --
    String response =
        mockMvc
            .perform(get(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration"))
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
      "Update Chaining Configuration should return 404 when INJECT_CHAINING feature is disabled")
  void updateChainingConfiguration_shouldReturnNotFoundWhenFeatureDisabled() throws Exception {
    // -- PREPARE --
    openAEVConfig.setEnabledDevFeatures("");
    clearFeatureCache();
    Workflow workflow = createTemplateWorkflow();
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder().rateLimitEnabled(false).safeModeEnabled(true).build();

    // -- EXECUTE & ASSERT --
    String response =
        mockMvc
            .perform(
                put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
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
  @DisplayName("Update Chaining Configuration should update and persist configuration")
  void updateChainingConfiguration_shouldUpdateAndPersistConfiguration() throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    ChainingConfiguration existingConfiguration =
        attachChainingConfiguration(workflow, false, 1, 5, true, 120);

    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
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
                put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT RESPONSE --
    JsonNode body = new ObjectMapper().readTree(response);

    assertTrue(body.get("chaining_configuration_rate_limit_enabled").asBoolean());
    assertEquals(7, body.get("chaining_configuration_max_attempts").asInt());
    assertEquals(15, body.get("chaining_configuration_max_temporal_rate_seconds").asInt());
    assertTrue(body.get("chaining_configuration_timeout_enabled").asBoolean());
    assertEquals(5400L, body.get("chaining_configuration_timeout_seconds").asLong());
    assertFalse(body.get("chaining_configuration_safe_mode_enabled").asBoolean());

    // -- ASSERT DATABASE --
    ChainingConfiguration savedConfiguration =
        chainingConfigurationRepository.findById(existingConfiguration.getId()).orElseThrow();
    assertTrue(savedConfiguration.isRateLimitEnabled());
    assertEquals(7, savedConfiguration.getMaxAttempts());
    assertEquals(15L, savedConfiguration.getMaxTemporalRateSeconds());
    assertTrue(savedConfiguration.isTimeoutEnabled());
    assertEquals(5400L, savedConfiguration.getTimeoutSeconds());
    assertFalse(savedConfiguration.isSafeModeEnabled());
  }

  @Test
  @DisplayName(
      "Update Chaining Configuration should return 404 when chaining configuration is missing")
  void updateChainingConfiguration_shouldReturnNotFoundWhenConfigurationMissing() throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(5)
            .maxTemporalRateSeconds(10L)
            .timeoutEnabled(true)
            .timeoutSeconds(1800L) // 30 min
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(
                put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(
        "Element not found: Chaining configuration not found for this workflow: "
            + workflow.getId(),
        JsonPath.read(response, "$.message"));
  }

  @Test
  @DisplayName(
      "Update Chaining Configuration should return 400 when rate limit max attempts is below minimum")
  void updateChainingConfiguration_shouldReturnBadRequestWhenMaxAttemptsBelowMin()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    attachChainingConfiguration(workflow, false, 1, 5, true, 120);
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(0) // below @Min(1)
            .maxTemporalRateSeconds(10L)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Update Chaining Configuration should return 400 when rate limit max attempts exceeds maximum")
  void updateChainingConfiguration_shouldReturnBadRequestWhenMaxAttemptsAboveMax()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    attachChainingConfiguration(workflow, false, 1, 5, true, 120);
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(100) // above @Max(99)
            .maxTemporalRateSeconds(10L)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Update Chaining Configuration should return 400 when max temporal rate seconds is below minimum")
  void updateChainingConfiguration_shouldReturnBadRequestWhenMaxTemporalRateSecondsBelowMin()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    attachChainingConfiguration(workflow, false, 1, 5, true, 120);
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(3)
            .maxTemporalRateSeconds(0L) // below @Min(1)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Update Chaining Configuration should return 400 when max temporal rate seconds exceeds maximum")
  void updateChainingConfiguration_shouldReturnBadRequestWhenMaxTemporalRateSecondsAboveMax()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    attachChainingConfiguration(workflow, false, 1, 5, true, 120);
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .rateLimitEnabled(true)
            .maxAttempts(3)
            .maxTemporalRateSeconds(60L) // above @Max(59)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Update Chaining Configuration should return 400 when timeout seconds exceed maximum")
  void updateChainingConfiguration_shouldReturnBadRequestWhenTimeoutSecondsAboveMax()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    attachChainingConfiguration(workflow, false, 1, 5, true, 120);
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .timeoutEnabled(true)
            .timeoutSeconds(86401L) // above @Max(86400)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Update Chaining Configuration should return 400 when timeout seconds are negative")
  void updateChainingConfiguration_shouldReturnBadRequestWhenTimeoutSecondsNegative()
      throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    attachChainingConfiguration(workflow, false, 1, 5, true, 120);
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .timeoutEnabled(true)
            .timeoutSeconds(-1L) // below @Min(0)
            .safeModeEnabled(true)
            .build();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(WORKFLOW_URI + "/" + workflow.getId() + "/chaining-configuration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

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

  private ChainingConfiguration attachChainingConfiguration(
      Workflow workflow,
      boolean rateLimitEnabled,
      int maxAttempts,
      int maxTemporalRateSeconds,
      boolean timeOutEnabled,
      int timeOutSeconds) {
    ChainingConfiguration configuration = new ChainingConfiguration();
    configuration.setRateLimitEnabled(rateLimitEnabled);
    configuration.setMaxAttempts(maxAttempts);
    configuration.setMaxTemporalRateSeconds((long) maxTemporalRateSeconds);
    configuration.setTimeoutEnabled(timeOutEnabled);
    configuration.setTimeoutSeconds((long) timeOutSeconds);
    configuration.setSafeModeEnabled(true);
    workflowComposer.forWorkflow(workflow).withChainingConfiguration(configuration).persist();
    return chainingConfigurationRepository.findById(configuration.getId()).orElseThrow();
  }
}
