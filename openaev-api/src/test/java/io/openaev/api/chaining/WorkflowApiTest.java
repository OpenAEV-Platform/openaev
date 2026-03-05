package io.openaev.api.chaining;

import static io.openaev.api.chaining.WorkflowApi.CHAINING_API;
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
import io.openaev.api.chaining.dto.ChainingRateLimitInput;
import io.openaev.api.chaining.dto.ChainingTimeOutInput;
import io.openaev.database.model.ChainingConfiguration;
import io.openaev.database.model.ChainingRateLimit;
import io.openaev.database.model.ChainingTimeOut;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.ChainingConfigurationRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Test
  @DisplayName("Fetch Chaining Configuration should return configuration for a template workflow")
  void fetchChainingConfiguration_shouldReturnConfiguration() throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    attachChainingConfiguration(workflow, true, 3, 10, true, 3660);

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(
                get(
                    CHAINING_API + WORKFLOW_URI + "/{workflowId}/chaining-configuration",
                    workflow.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    JsonNode body = new ObjectMapper().readTree(response);

    JsonNode rateLimit = body.get("chaining_configuration_rate_limit");
    assertTrue(rateLimit.get("chaining_enable_rate_limit").asBoolean());
    assertEquals(3, rateLimit.get("chaining_max_attempts").asInt());
    assertEquals(10, rateLimit.get("chaining_max_temporal_rate_minutes").asInt());

    JsonNode timeOut = body.get("chaining_configuration_time_out");
    assertTrue(timeOut.get("chaining_enable_time_out").asBoolean());
    assertEquals(1, timeOut.get("chaining_time_out_hours").asInt());
    assertEquals(1, timeOut.get("chaining_time_out_minutes").asInt());

    assertTrue(body.get("chaining_configuration_enable_safe_mode").asBoolean());
  }

  @Test
  @DisplayName("Fetch Chaining Configuration should return 404 when workflow does not exist")
  void fetchChainingConfiguration_shouldReturnNotFoundWhenWorkflowMissing() throws Exception {
    // -- PREPARE --
    String workflowId = "missing-workflow-id";

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(
                get(
                    CHAINING_API + WORKFLOW_URI + "/{workflowId}/chaining-configuration",
                    workflowId))
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
  @DisplayName("Update Chaining Configuration should update and persist configuration")
  void updateChainingConfiguration_shouldUpdateAndPersistConfiguration() throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    ChainingConfiguration existingConfiguration =
        attachChainingConfiguration(workflow, false, 1, 5, true, 120);

    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .rateLimit(
                ChainingRateLimitInput.builder()
                    .isRateLimit(true)
                    .maxAttempts(7)
                    .maxTemporalRateMinutes(15)
                    .build())
            .timeOut(
                ChainingTimeOutInput.builder()
                    .isTimeOut(true)
                    .timeOutHours(1)
                    .timeOutMinutes(30)
                    .build())
            .isSafeMode(false)
            .build();

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(
                put(
                        CHAINING_API + WORKFLOW_URI + "/{workflowId}/chaining-configuration",
                        workflow.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT RESPONSE --
    JsonNode body = new ObjectMapper().readTree(response);

    JsonNode rateLimit = body.get("chaining_configuration_rate_limit");
    assertTrue(rateLimit.get("chaining_enable_rate_limit").asBoolean());
    assertEquals(7, rateLimit.get("chaining_max_attempts").asInt());
    assertEquals(15, rateLimit.get("chaining_max_temporal_rate_minutes").asInt());

    JsonNode timeOut = body.get("chaining_configuration_time_out");
    assertTrue(timeOut.get("chaining_enable_time_out").asBoolean());
    assertEquals(1, timeOut.get("chaining_time_out_hours").asInt());
    assertEquals(30, timeOut.get("chaining_time_out_minutes").asInt());

    assertFalse(body.get("chaining_configuration_enable_safe_mode").asBoolean());

    // -- ASSERT DATABASE --
    ChainingConfiguration savedConfiguration =
        chainingConfigurationRepository.findById(existingConfiguration.getId()).orElseThrow();
    assertNotNull(savedConfiguration.getRateLimit());
    assertTrue(savedConfiguration.getRateLimit().isEnableRateLimit());
    assertEquals(7, savedConfiguration.getRateLimit().getMaxAttempts());
    assertEquals(15, savedConfiguration.getRateLimit().getMaxTemporalRateMinutes());
    assertNotNull(savedConfiguration.getTimeOut());
    assertTrue(savedConfiguration.getTimeOut().isEnableTimeOut());
    assertEquals(5400, savedConfiguration.getTimeOut().getTimeOutSeconds());
    assertFalse(savedConfiguration.isSafeMode());
  }

  @Test
  @DisplayName(
      "Update Chaining Configuration should return 404 when chaining configuration is missing")
  void updateChainingConfiguration_shouldReturnNotFoundWhenConfigurationMissing() throws Exception {
    // -- PREPARE --
    Workflow workflow = createTemplateWorkflow();
    ChainingConfigurationInput input =
        ChainingConfigurationInput.builder()
            .rateLimit(
                ChainingRateLimitInput.builder()
                    .isRateLimit(true)
                    .maxAttempts(5)
                    .maxTemporalRateMinutes(10)
                    .build())
            .timeOut(
                ChainingTimeOutInput.builder()
                    .isTimeOut(true)
                    .timeOutHours(0)
                    .timeOutMinutes(30)
                    .build())
            .isSafeMode(true)
            .build();

    // -- EXECUTE --
    String response =
        mockMvc
            .perform(
                put(
                        CHAINING_API + WORKFLOW_URI + "/{workflowId}/chaining-configuration",
                        workflow.getId())
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

  private Workflow createTemplateWorkflow() {
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setStatus(WorkflowStatus.TEMPLATE);
    Exercise exercise = ExerciseFixture.getExercise();
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
      int maxTemporalRateMinutes,
      boolean timeOutEnabled,
      int timeOutSeconds) {
    ChainingRateLimit rateLimit = new ChainingRateLimit();
    rateLimit.setEnableRateLimit(rateLimitEnabled);
    rateLimit.setMaxAttempts(maxAttempts);
    rateLimit.setMaxTemporalRateMinutes(maxTemporalRateMinutes);

    ChainingTimeOut timeOut = new ChainingTimeOut();
    timeOut.setEnableTimeOut(timeOutEnabled);
    timeOut.setTimeOutSeconds(timeOutSeconds);

    ChainingConfiguration configuration = new ChainingConfiguration();
    configuration.setRateLimit(rateLimit);
    configuration.setTimeOut(timeOut);
    configuration.setSafeMode(true);
    workflowComposer.forWorkflow(workflow).withChainingConfiguration(configuration).persist();
    return chainingConfigurationRepository.findById(configuration.getId()).orElseThrow();
  }
}
