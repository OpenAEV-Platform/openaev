package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.mapper.MapperApi;
import io.openaev.rest.report.ReportApi;
import io.openaev.rest.report.form.ReportInjectCommentInput;
import io.openaev.rest.report.form.ReportInput;
import io.openaev.rest.report.model.Report;
import io.openaev.rest.report.service.ReportService;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
public class ReportApiTest extends IntegrationTest {

  private MockMvc mvc;

  @Mock private ReportService reportService;
  @Mock private ExerciseService exerciseService;
  @Mock private InjectService injectService;

  @Autowired private ObjectMapper objectMapper;

  private Exercise exercise;
  private Report report;
  private ReportInput reportInput;

  @BeforeEach
  void before() throws IllegalAccessException, NoSuchFieldException {
    ReportApi reportApi = new ReportApi(exerciseService, reportService, injectService);
    Field sessionContextField = MapperApi.class.getSuperclass().getDeclaredField("mapper");
    sessionContextField.setAccessible(true);
    sessionContextField.set(reportApi, objectMapper);
    mvc = MockMvcBuilders.standaloneSetup(reportApi).build();

    exercise = new Exercise();
    exercise.setName("Exercise name");
    exercise.setId("exercise123");
    report = new Report();
    report.setId(UUID.randomUUID().toString());
    reportInput = new ReportInput();
    reportInput.setName("Report name");
  }

  @Nested
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  @DisplayName("Reports for exercise")
  class ReportsForExercise {
    @DisplayName("Create report")
    @Test
    void createReportForExercise() throws Exception {
      // -- PREPARE --
      when(exerciseService.exercise(anyString())).thenReturn(exercise);
      when(reportService.updateReport(any(Report.class), any(ReportInput.class)))
          .thenReturn(report);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  MockMvcRequestBuilders.post("/api/exercises/" + exercise.getId() + "/reports")
                      .content(asJsonString(reportInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      verify(exerciseService).exercise(exercise.getId());
      assertNotNull(response);
      assertEquals(JsonPath.read(response, "$.report_id"), report.getId());
    }

    @DisplayName("Retrieve reports")
    @Test
    void retrieveReportForExercise() throws Exception {
      // PREPARE
      List<Report> reports = List.of(report);
      when(reportService.reportsFromExercise(anyString())).thenReturn(reports);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  MockMvcRequestBuilders.get("/api/exercises/fakeExercisesId123/reports")
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      verify(reportService).reportsFromExercise("fakeExercisesId123");
      assertNotNull(response);
      assertEquals(JsonPath.read(response, "$[0].report_id"), report.getId());
    }

    @DisplayName("Update Report")
    @Test
    void updateReportForExercise() throws Exception {
      // -- PREPARE --
      report.setExercise(exercise);
      when(reportService.report(any())).thenReturn(report);
      when(reportService.updateReport(any(Report.class), any(ReportInput.class)))
          .thenReturn(report);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  MockMvcRequestBuilders.put(
                          "/api/exercises/" + exercise.getId() + "/reports/" + report.getId())
                      .content(asJsonString(reportInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      report.setName("fake");
      verify(reportService).report(UUID.fromString(report.getId()));
      verify(reportService).updateReport(report, reportInput);
      assertNotNull(response);
      assertEquals(JsonPath.read(response, "$.report_id"), report.getId());
    }

    @DisplayName("Update report inject comment")
    @Test
    void updateReportInjectCommentTest() throws Exception {
      // -- PREPARE --
      Inject inject = new Inject();
      inject.setTitle("Test inject");
      inject.setId(UUID.randomUUID().toString());
      inject.setExercise(exercise);
      report.setExercise(exercise);
      ReportInjectCommentInput injectCommentInput = new ReportInjectCommentInput();
      injectCommentInput.setInjectId(inject.getId());
      injectCommentInput.setComment("Comment test");

      when(reportService.report(any())).thenReturn(report);
      when(injectService.inject(any())).thenReturn(inject);
      when(reportService.updateReportInjectComment(
              any(Report.class), any(Inject.class), any(ReportInjectCommentInput.class)))
          .thenReturn(null);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  MockMvcRequestBuilders.put(
                          "/api/exercises/"
                              + exercise.getId()
                              + "/reports/"
                              + report.getId()
                              + "/inject-comments")
                      .content(asJsonString(injectCommentInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      verify(reportService).report(UUID.fromString(report.getId()));
      verify(injectService).inject(inject.getId());
      verify(reportService).updateReportInjectComment(report, inject, injectCommentInput);
      assertNotNull(response);
    }

    @DisplayName("Delete Report")
    @Test
    void deleteReportForExercise() throws Exception {
      // -- PREPARE --
      report.setExercise(exercise);
      when(reportService.report(any())).thenReturn(report);

      // -- EXECUTE --
      mvc.perform(
              MockMvcRequestBuilders.delete(
                      "/api/exercises/" + exercise.getId() + "/reports/" + report.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(PaginationFixture.getDefault().textSearch("").build())))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      verify(reportService, times(1)).deleteReport(UUID.fromString(report.getId()));
    }
  }
}
