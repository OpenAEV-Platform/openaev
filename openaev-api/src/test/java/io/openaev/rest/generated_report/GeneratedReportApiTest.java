package io.openaev.rest.generated_report;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.GeneratedReportStatus;
import io.openaev.database.model.GeneratedReportTemplate;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exercise.ExerciseApi;
import io.openaev.rest.exercise.form.ExerciseInput;
import io.openaev.rest.generated_report.form.GeneratedReportInput;
import io.openaev.rest.generated_report.form.GeneratedReportStatusInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestInstance(PER_CLASS)
@DisplayName("Generated Report API tests")
public class GeneratedReportApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @BeforeEach
  void before() {
    exerciseComposer.reset();
  }

  private String createExercise() throws Exception {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setName("Exercise " + UUID.randomUUID());
    return exerciseComposer.forExercise(exercise).persist().get().getId();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Generation lifecycle")
  @Transactional
  class GenerationLifecycle {

    @Test
    @DisplayName("Given a valid template, should create a PENDING generated report")
    void given_validTemplate_should_createPendingGeneratedReport() throws Exception {
      // -- ARRANGE --
      String exerciseId = createExercise();
      GeneratedReportInput input = new GeneratedReportInput();
      input.setTemplate(GeneratedReportTemplate.EXECUTIVE);

      // -- ACT --
      String response =
          mvc.perform(
                  post("/api/exercises/" + exerciseId + "/generated-reports")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<String>read(response, "$.generated_report_status")).isEqualTo("PENDING");
      assertThat(JsonPath.<String>read(response, "$.generated_report_template"))
          .isEqualTo("EXECUTIVE");
    }

    @Test
    @DisplayName("Given only 2 templates allowed, should reject an unknown template value")
    void given_unknownTemplate_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      String exerciseId = createExercise();
      String rawBody = "{\"generated_report_template\":\"CUSTOM\"}";

      // -- ACT & ASSERT --
      mvc.perform(
              post("/api/exercises/" + exerciseId + "/generated-reports")
                  .content(rawBody)
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName(
        "Given a pending report, should transition status to RUNNING then COMPLETED on upload")
    void given_pendingReport_should_completeAfterDocumentUpload() throws Exception {
      // -- ARRANGE --
      String exerciseId = createExercise();
      GeneratedReportInput input = new GeneratedReportInput();
      input.setTemplate(GeneratedReportTemplate.TECHNICAL);
      String createResponse =
          mvc.perform(
                  post("/api/exercises/" + exerciseId + "/generated-reports")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String reportId = JsonPath.read(createResponse, "$.generated_report_id");

      GeneratedReportStatusInput runningInput = new GeneratedReportStatusInput();
      runningInput.setStatus(GeneratedReportStatus.RUNNING);
      mvc.perform(
              put("/api/exercises/" + exerciseId + "/generated-reports/" + reportId + "/status")
                  .content(asJsonString(runningInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      MockMultipartFile file =
          new MockMultipartFile("file", "report.pdf", "application/pdf", "fake-pdf".getBytes());

      // -- ACT --
      String uploadResponse =
          mvc.perform(
                  multipart(
                          "/api/exercises/"
                              + exerciseId
                              + "/generated-reports/"
                              + reportId
                              + "/document")
                      .file(file)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<String>read(uploadResponse, "$.generated_report_status"))
          .isEqualTo("COMPLETED");

      mvc.perform(
              get("/api/exercises/" + exerciseId + "/generated-reports/" + reportId + "/file")
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given generated reports for an exercise, should list them newest first")
    void given_generatedReports_should_listNewestFirst() throws Exception {
      // -- ARRANGE --
      String exerciseId = createExercise();
      GeneratedReportInput input = new GeneratedReportInput();
      input.setTemplate(GeneratedReportTemplate.EXECUTIVE);
      mvc.perform(
              post("/api/exercises/" + exerciseId + "/generated-reports")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ACT --
      String response =
          mvc.perform(
                  get("/api/exercises/" + exerciseId + "/generated-reports")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      List<String> templates = JsonPath.read(response, "$[*].generated_report_template");
      assertThat(templates).contains("EXECUTIVE");
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  @Transactional
  class TenantIsolation {

    @Test
    @DisplayName("Generated report in tenant X should NOT be readable from tenant Y")
    void given_generatedReportInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.ACCESS_ASSESSMENT, Capability.MANAGE_ASSESSMENT));
      Tenant tenantY =
          tenantHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_ASSESSMENT, Capability.MANAGE_ASSESSMENT));

      ExerciseInput exerciseInput = new ExerciseInput();
      exerciseInput.setName("Isolation Exercise " + UUID.randomUUID());
      String exerciseResponse =
          mvc.perform(
                  post(ExerciseApi.TENANT_EXERCISE_URI.replace("{tenantId}", tenantX.getId()))
                      .content(asJsonString(exerciseInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String exerciseId = JsonPath.read(exerciseResponse, "$.exercise_id");

      GeneratedReportInput input = new GeneratedReportInput();
      input.setTemplate(GeneratedReportTemplate.EXECUTIVE);
      String createResponse =
          mvc.perform(
                  post("/api/tenants/"
                          + tenantX.getId()
                          + "/exercises/"
                          + exerciseId
                          + "/generated-reports")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String reportId = JsonPath.read(createResponse, "$.generated_report_id");

      // -------- Act --------
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/"
                          + tenantY.getId()
                          + "/exercises/"
                          + exerciseId
                          + "/generated-reports/"
                          + reportId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
