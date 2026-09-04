package io.openaev.rest.reporting;

import static io.openaev.rest.reporting.ReportingApi.REPORTINGS_URI;
import static io.openaev.rest.reporting.ReportingApi.TENANT_REPORTINGS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ReportingFixture.REPORTING_NAME;
import static io.openaev.utils.fixtures.ReportingFixture.createDailyScheduleInput;
import static io.openaev.utils.fixtures.ReportingFixture.createDefaultReporting;
import static io.openaev.utils.fixtures.ReportingFixture.createDefaultReportingInput;
import static io.openaev.utils.fixtures.ReportingFixture.createReporting;
import static io.openaev.utils.fixtures.ReportingFixture.createReportingInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingSchedulePeriod;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.database.repository.ReportingRepository;
import io.openaev.database.repository.ReportingScheduleRepository;
import io.openaev.rest.reporting.form.ReportingGenerateInput;
import io.openaev.rest.reporting.form.ReportingInput;
import io.openaev.rest.reporting.form.ReportingScheduleInput;
import io.openaev.rest.reporting.service.ReportingRenderer;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.ReportingComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ReportingApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ReportingRepository reportingRepository;
  @Autowired private ReportingGenerationRepository reportingGenerationRepository;
  @Autowired private ReportingScheduleRepository reportingScheduleRepository;
  @Autowired private ReportingComposer reportingComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  // The real renderer (Playwright) drives a headless browser; tests only assert the API
  // contract around the PENDING generation row, so the rendering engine is mocked out.
  @MockitoBean private ReportingRenderer reportingRenderer;

  @BeforeEach
  void setUp() {
    reportingComposer.reset();
  }

  private Reporting persistDefaultReporting() {
    return reportingComposer.forReporting(createDefaultReporting()).persist().get();
  }

  // -- CRUD --

  @Nested
  @DisplayName("CRUD lifecycle")
  class CrudLifecycle {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_REPORTINGS})
    @DisplayName("Given a valid input, should create a reporting template")
    void given_validInput_should_createReporting() throws Exception {
      // -- Arrange --
      ReportingInput input = createDefaultReportingInput();

      // -- Act --
      String response =
          mvc.perform(
                  post(REPORTINGS_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.reporting_name").value(REPORTING_NAME))
              .andExpect(jsonPath("$.reporting_context_type").value("PLATFORM"))
              .andExpect(jsonPath("$.reporting_default_format").value("PDF"))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String reportingId = JsonPath.read(response, "$.reporting_id");
      assertThat(reportingRepository.existsById(reportingId)).isTrue();
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given a reporting id, should return the reporting")
    void given_reportingId_should_returnReporting() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();

      // -- Act & Assert --
      mvc.perform(get(REPORTINGS_URI + "/" + reporting.getId()).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reporting_id").value(reporting.getId()))
          .andExpect(jsonPath("$.reporting_name").value(REPORTING_NAME));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given an unknown reporting id, should return not found")
    void given_unknownReportingId_should_returnNotFound() throws Exception {
      mvc.perform(get(REPORTINGS_URI + "/" + UUID.randomUUID()).with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given a name filter, search should return only the matching page")
    void given_nameFilter_should_searchWithPagination() throws Exception {
      // -- Arrange --
      String token = "SearchCheck-" + UUID.randomUUID().toString().substring(0, 8);
      reportingComposer
          .forReporting(createReporting(token + " AAA", ReportingContextType.PLATFORM, null))
          .persist();
      reportingComposer
          .forReporting(createReporting(token + " ZZZ", ReportingContextType.PLATFORM, null))
          .persist();
      reportingComposer
          .forReporting(createReporting("Unrelated", ReportingContextType.PLATFORM, null))
          .persist();

      SearchPaginationInput input =
          PaginationFixture.getDefault()
              .textSearch(token)
              .size(1)
              .sorts(List.of(new SortField("reporting_name", "desc", null)))
              .build();

      // -- Act --
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert -- 2 matches in total, 1 per page, sorted desc so ZZZ comes first
      assertEquals(Integer.valueOf(2), JsonPath.read(response, "$.totalElements"));
      assertEquals(Integer.valueOf(1), JsonPath.read(response, "$.numberOfElements"));
      assertEquals(token + " ZZZ", JsonPath.read(response, "$.content[0].reporting_name"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_REPORTINGS})
    @DisplayName("Given an updated input, should update the reporting")
    void given_updatedInput_should_updateReporting() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();
      ReportingInput input =
          createReportingInput("Updated name", ReportingContextType.PLATFORM, null);
      input.setDescription("Updated description");

      // -- Act & Assert --
      mvc.perform(
              put(REPORTINGS_URI + "/" + reporting.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reporting_name").value("Updated name"))
          .andExpect(jsonPath("$.reporting_description").value("Updated description"));

      entityManager.flush();
      entityManager.clear();
      Reporting updated = reportingRepository.findById(reporting.getId()).orElseThrow();
      assertThat(updated.getName()).isEqualTo("Updated name");
      assertThat(updated.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_REPORTINGS})
    @DisplayName("Given a reporting id, should delete the reporting")
    void given_reportingId_should_deleteReporting() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();

      // -- Act & Assert --
      mvc.perform(delete(REPORTINGS_URI + "/" + reporting.getId()).with(csrf()))
          .andExpect(status().isNoContent());

      assertThat(reportingRepository.existsById(reporting.getId())).isFalse();
    }
  }

  // -- RBAC --

  @Nested
  @DisplayName("RBAC")
  class Rbac {

    @Test
    @WithMockUser
    @DisplayName("Given no capabilities, should be forbidden to create")
    void given_noCapabilities_should_forbidCreate() throws Exception {
      mvc.perform(
              post(REPORTINGS_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(createDefaultReportingInput()))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("Given no capabilities, should be forbidden to search")
    void given_noCapabilities_should_forbidSearch() throws Exception {
      mvc.perform(
              post(REPORTINGS_URI + "/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(PaginationFixture.getDefault().build()))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given ACCESS_REPORTINGS only, should read and search")
    void given_accessReportings_should_readAndSearch() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();

      // -- Act & Assert --
      mvc.perform(get(REPORTINGS_URI + "/" + reporting.getId()).with(csrf()))
          .andExpect(status().isOk());
      mvc.perform(
              post(REPORTINGS_URI + "/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(PaginationFixture.getDefault().build()))
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given ACCESS_REPORTINGS only, should be forbidden to create")
    void given_accessReportings_should_forbidCreate() throws Exception {
      mvc.perform(
              post(REPORTINGS_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(createDefaultReportingInput()))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given ACCESS_REPORTINGS only, should be forbidden to update")
    void given_accessReportings_should_forbidUpdate() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();

      // -- Act & Assert --
      mvc.perform(
              put(REPORTINGS_URI + "/" + reporting.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(createDefaultReportingInput()))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given ACCESS_REPORTINGS only, should be forbidden to delete")
    void given_accessReportings_should_forbidDelete() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();

      // -- Act & Assert --
      mvc.perform(delete(REPORTINGS_URI + "/" + reporting.getId()).with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_REPORTINGS})
    @DisplayName("Given MANAGE_REPORTINGS only, should be forbidden to delete")
    void given_manageReportings_should_forbidDelete() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();

      // -- Act & Assert --
      mvc.perform(delete(REPORTINGS_URI + "/" + reporting.getId()).with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  // -- SUBJECT RBAC --

  // A reporting must never be more visible than its subject: a user without access to a
  // scenario must not see (or generate) the reportings built around that scenario.
  @Nested
  @DisplayName("Subject RBAC")
  class SubjectRbac {

    private Reporting persistScenarioReporting(String scenarioId) {
      return reportingComposer
          .forReporting(
              createReporting("Scenario report", ReportingContextType.SCENARIO, scenarioId))
          .persist()
          .get();
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given no access to the scenario, its reporting should be forbidden")
    void given_noSubjectAccess_should_forbidRead() throws Exception {
      // -- Arrange --
      String scenarioId = UUID.randomUUID().toString();
      Reporting reporting = persistScenarioReporting(scenarioId);

      // -- Act & Assert -- direct read, context listing and generations are all denied.
      // Subject-level denials surface as 404 (RestBehavior masks AccessDeniedException to
      // avoid disclosing that the resource exists).
      mvc.perform(get(REPORTINGS_URI + "/" + reporting.getId()).with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(get(REPORTINGS_URI + "/context/SCENARIO/" + scenarioId).with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(get(REPORTINGS_URI + "/" + reporting.getId() + "/generations").with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given no access to the scenario, search should not list its reporting")
    void given_noSubjectAccess_should_filterSearch() throws Exception {
      // -- Arrange -- one inaccessible scenario reporting, one platform reporting
      Reporting scenarioReporting = persistScenarioReporting(UUID.randomUUID().toString());
      Reporting platformReporting = persistDefaultReporting();

      // -- Act --
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(PaginationFixture.getDefault().build()))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert -- only the platform reporting is visible
      List<String> ids = JsonPath.read(response, "$.content[*].reporting_id");
      assertThat(ids).contains(platformReporting.getId());
      assertThat(ids).doesNotContain(scenarioReporting.getId());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS})
    @DisplayName("Given an OBSERVER grant on the scenario, its reporting should be visible")
    void given_observerGrant_should_allowReadAndSearch() throws Exception {
      // -- Arrange --
      String scenarioId = UUID.randomUUID().toString();
      Reporting reporting = persistScenarioReporting(scenarioId);
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SCENARIO, Grant.GRANT_TYPE.OBSERVER, scenarioId);

      // -- Act & Assert -- direct read and context listing succeed
      mvc.perform(get(REPORTINGS_URI + "/" + reporting.getId()).with(csrf()))
          .andExpect(status().isOk());
      mvc.perform(get(REPORTINGS_URI + "/context/SCENARIO/" + scenarioId).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1));

      // -- Act & Assert -- and the search now lists the reporting
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(PaginationFixture.getDefault().build()))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<String> ids = JsonPath.read(response, "$.content[*].reporting_id");
      assertThat(ids).contains(reporting.getId());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS, Capability.ACCESS_ASSESSMENT})
    @DisplayName("Given the assessment capability, scenario reportings should be visible")
    void given_assessmentCapability_should_allowRead() throws Exception {
      // -- Arrange --
      Reporting reporting = persistScenarioReporting(UUID.randomUUID().toString());

      // -- Act & Assert --
      mvc.perform(get(REPORTINGS_URI + "/" + reporting.getId()).with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_REPORTINGS, Capability.ACCESS_REPORTINGS})
    @DisplayName("Given no access to the scenario, generate and create should be forbidden")
    void given_noSubjectAccess_should_forbidGenerateAndCreate() throws Exception {
      // -- Arrange --
      String scenarioId = UUID.randomUUID().toString();
      Reporting reporting = persistScenarioReporting(scenarioId);
      ReportingGenerateInput generateInput = new ReportingGenerateInput();
      generateInput.setFormat(ReportingFormat.PDF);

      // -- Act & Assert -- generating an existing reporting about the scenario is denied
      // (404: subject-level denials are masked to avoid disclosing the resource exists)
      mvc.perform(
              post(REPORTINGS_URI + "/" + reporting.getId() + "/generate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(generateInput))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // -- Act & Assert -- creating a new reporting about the scenario is denied too
      mvc.perform(
              post(REPORTINGS_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          createReportingInput(
                              "Sneaky report", ReportingContextType.SCENARIO, scenarioId)))
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }
  }

  // -- CONTEXT --

  @Nested
  @DisplayName("Context endpoint")
  // ACCESS_ASSESSMENT: the simulation-context listing requires read access to the subject
  @WithMockUser(withCapabilities = {Capability.ACCESS_REPORTINGS, Capability.ACCESS_ASSESSMENT})
  class ContextEndpoint {

    @Test
    @DisplayName("Given a SIMULATION reporting, should be listed for its simulation only")
    void given_simulationReporting_should_beListedForItsSimulationOnly() throws Exception {
      // -- Arrange --
      String simulationId = UUID.randomUUID().toString();
      Reporting reporting =
          reportingComposer
              .forReporting(
                  createReporting(
                      "Simulation report", ReportingContextType.SIMULATION, simulationId))
              .persist()
              .get();

      // -- Act & Assert -- reporting is returned for its own simulation id
      mvc.perform(get(REPORTINGS_URI + "/context/SIMULATION/" + simulationId).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].reporting_id").value(reporting.getId()));

      // -- Act & Assert -- and not for another simulation id
      mvc.perform(get(REPORTINGS_URI + "/context/SIMULATION/" + UUID.randomUUID()).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Given a PLATFORM reporting, should be listed by the PLATFORM context")
    void given_platformReporting_should_beListedByPlatformContext() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();

      // -- Act & Assert --
      mvc.perform(get(REPORTINGS_URI + "/context/PLATFORM").with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].reporting_id").value(reporting.getId()));
    }
  }

  // -- GENERATIONS --

  // Note: capabilities granted through @WithMockUser are literal (the test role fixture does not
  // expand parents the way RoleService.resolveWithParents does), so READ endpoints need
  // ACCESS_REPORTINGS explicitly next to MANAGE_REPORTINGS.
  @Nested
  @DisplayName("Generations")
  @WithMockUser(withCapabilities = {Capability.MANAGE_REPORTINGS, Capability.ACCESS_REPORTINGS})
  class Generations {

    @Test
    @DisplayName("Given a reporting, generate should return a pending generation")
    void given_reporting_should_returnPendingGeneration() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();
      ReportingGenerateInput input = defaultGenerateInput();

      // -- Act --
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/" + reporting.getId() + "/generate")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.reporting_generation_status").value("PENDING"))
              .andExpect(jsonPath("$.reporting_generation_trigger").value("MANUAL"))
              .andExpect(jsonPath("$.reporting_generation_format").value("PDF"))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert -- the generation row is persisted and handed to the renderer
      String generationId = JsonPath.read(response, "$.reporting_generation_id");
      assertThat(reportingGenerationRepository.existsById(generationId)).isTrue();
      verify(reportingRenderer).render(any(), any());
    }

    @Test
    @DisplayName("Given a generated reporting, generations should be listed and readable")
    void given_generatedReporting_should_listAndReadGenerations() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/" + reporting.getId() + "/generate")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(defaultGenerateInput()))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String generationId = JsonPath.read(response, "$.reporting_generation_id");

      // -- Act & Assert -- list
      mvc.perform(get(REPORTINGS_URI + "/" + reporting.getId() + "/generations").with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].reporting_generation_id").value(generationId));

      // -- Act & Assert -- single read
      mvc.perform(get(REPORTINGS_URI + "/generations/" + generationId).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reporting_generation_id").value(generationId));
    }

    @Test
    @DisplayName("Given a non successful generation, file download should be a bad request")
    void given_nonSuccessfulGeneration_should_rejectFileDownload() throws Exception {
      // -- Arrange -- the mocked renderer leaves the generation PENDING
      Reporting reporting = persistDefaultReporting();
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/" + reporting.getId() + "/generate")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(defaultGenerateInput()))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String generationId = JsonPath.read(response, "$.reporting_generation_id");

      // -- Act & Assert --
      mvc.perform(get(REPORTINGS_URI + "/generations/" + generationId + "/file").with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given a generation id, should delete the generation")
    void given_generationId_should_deleteGeneration() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/" + reporting.getId() + "/generate")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(defaultGenerateInput()))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String generationId = JsonPath.read(response, "$.reporting_generation_id");

      // -- Act & Assert --
      mvc.perform(delete(REPORTINGS_URI + "/generations/" + generationId).with(csrf()))
          .andExpect(status().isNoContent());

      assertThat(reportingGenerationRepository.existsById(generationId)).isFalse();
    }

    private ReportingGenerateInput defaultGenerateInput() {
      ReportingGenerateInput input = new ReportingGenerateInput();
      input.setFormat(ReportingFormat.PDF);
      return input;
    }
  }

  // -- SCHEDULES --

  @Nested
  @DisplayName("Schedules")
  @WithMockUser(withCapabilities = {Capability.MANAGE_REPORTINGS})
  class Schedules {

    @Test
    @DisplayName("Given a valid input, should create a schedule with its recipients")
    void given_validInput_should_createScheduleWithRecipients() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();
      String recipientUserId = testUserHolder.get().getId();
      ReportingScheduleInput input = createDailyScheduleInput("Daily report", "09:00");
      input.setRecipientUserIds(List.of(recipientUserId));
      input.setRecipientEmails(List.of("external@filigran.io"));

      // -- Act --
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/" + reporting.getId() + "/schedules")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.reporting_schedule_name").value("Daily report"))
              .andExpect(jsonPath("$.reporting_schedule_period").value("DAY"))
              .andExpect(jsonPath("$.reporting_schedule_time").value("09:00"))
              .andExpect(jsonPath("$.reporting_schedule_enabled").value(true))
              .andExpect(jsonPath("$.reporting_schedule_owner").value(recipientUserId))
              .andExpect(jsonPath("$.reporting_schedule_recipient_users.length()").value(1))
              .andExpect(jsonPath("$.reporting_schedule_recipient_users[0]").value(recipientUserId))
              .andExpect(
                  jsonPath("$.reporting_schedule_recipient_emails[0]")
                      .value("external@filigran.io"))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String scheduleId = JsonPath.read(response, "$.reporting_schedule_id");
      assertThat(reportingScheduleRepository.existsById(scheduleId)).isTrue();
    }

    @Test
    @DisplayName("Given an updated input, should update the schedule and its recipients")
    void given_updatedInput_should_updateSchedule() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();
      String scheduleId = createSchedule(reporting);

      ReportingScheduleInput update = createDailyScheduleInput("Weekly report", "1-08:30");
      update.setPeriod(ReportingSchedulePeriod.WEEK);
      update.setRecipientUserIds(List.of());
      update.setRecipientEmails(List.of("updated@filigran.io"));

      // -- Act & Assert --
      mvc.perform(
              put(REPORTINGS_URI + "/" + reporting.getId() + "/schedules/" + scheduleId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(update))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reporting_schedule_name").value("Weekly report"))
          .andExpect(jsonPath("$.reporting_schedule_period").value("WEEK"))
          .andExpect(jsonPath("$.reporting_schedule_time").value("1-08:30"))
          .andExpect(jsonPath("$.reporting_schedule_recipient_users.length()").value(0))
          .andExpect(
              jsonPath("$.reporting_schedule_recipient_emails[0]").value("updated@filigran.io"));
    }

    @Test
    @DisplayName("Given a schedule id, should delete the schedule")
    void given_scheduleId_should_deleteSchedule() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();
      String scheduleId = createSchedule(reporting);

      // -- Act & Assert --
      mvc.perform(
              delete(REPORTINGS_URI + "/" + reporting.getId() + "/schedules/" + scheduleId)
                  .with(csrf()))
          .andExpect(status().isNoContent());

      assertThat(reportingScheduleRepository.existsById(scheduleId)).isFalse();
    }

    @Test
    @DisplayName("Given a schedule of another reporting, update should return not found")
    void given_scheduleOfAnotherReporting_should_returnNotFound() throws Exception {
      // -- Arrange --
      Reporting reporting = persistDefaultReporting();
      String scheduleId = createSchedule(reporting);
      Reporting otherReporting =
          reportingComposer
              .forReporting(createReporting("Other", ReportingContextType.PLATFORM, null))
              .persist()
              .get();

      // -- Act & Assert --
      mvc.perform(
              put(REPORTINGS_URI + "/" + otherReporting.getId() + "/schedules/" + scheduleId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(createDailyScheduleInput("Hijack", "09:00")))
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    private String createSchedule(Reporting reporting) throws Exception {
      String response =
          mvc.perform(
                  post(REPORTINGS_URI + "/" + reporting.getId() + "/schedules")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createDailyScheduleInput("Daily report", "09:00")))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$.reporting_schedule_id");
    }
  }

  // -- TENANT ISOLATION --

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @DisplayName("Reporting created in tenant X should NOT be readable from tenant Y")
    void given_reportingInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_REPORTINGS, Capability.ACCESS_REPORTINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_REPORTINGS));

      // Seeded directly (native insert), not through the create endpoint: creating under tenant
      // X's path would set the tenant scope (TxCtx) to X on this test's wrapping transaction, and
      // the read call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Seeding bypasses that entirely.
      String reportingId = seedReportingInTenant(tenantX, "Isolation Read Reporting");

      // Act - read from tenant Y (expect 404)
      int responseStatus =
          mvc.perform(
                  get(tenantReportingsUri(tenantY) + "/" + reportingId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Reporting created in tenant X should be readable from tenant X")
    void given_reportingInTenantX_should_beReadableFromTenantX() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_REPORTINGS, Capability.ACCESS_REPORTINGS));

      String reportingId = createReportingInTenant(tenantX, "Same Tenant Reporting");

      // Act & Assert - read from same tenant should succeed
      mvc.perform(
              get(tenantReportingsUri(tenantX) + "/" + reportingId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reporting_name").value("Same Tenant Reporting"));
    }

    @Test
    @DisplayName("Reporting search in tenant Y should NOT return reportings from tenant X")
    void given_reportingInTenantX_should_notAppearInTenantYSearch() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_REPORTINGS, Capability.ACCESS_REPORTINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_REPORTINGS));

      // Seeded directly (native insert), not through the create endpoint: creating under tenant
      // X's path would set the tenant scope (TxCtx) to X on this test's wrapping transaction, and
      // the search call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Seeding bypasses that entirely.
      seedReportingInTenant(tenantX, "CrossTenantReportingSearch");

      // Act - search from tenant Y
      SearchPaginationInput searchInput =
          PaginationFixture.simpleTextSearch("CrossTenantReportingSearch");

      String searchResponse =
          mvc.perform(
                  post(tenantReportingsUri(tenantY) + "/search")
                      .content(asJsonString(searchInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert - no results from tenant X
      assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
    }

    @Test
    @DisplayName("Reporting created in tenant X should NOT be updatable from tenant Y")
    void given_reportingInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_REPORTINGS, Capability.ACCESS_REPORTINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_REPORTINGS, Capability.ACCESS_REPORTINGS));

      // Seeded directly (native insert): creating under tenant X's path via the API would set the
      // tenant scope (TxCtx) to X on this test's wrapping transaction, and the update call below
      // sets it to Y - the aspect refuses a scope change within one transaction (see
      // TenantScopeTransactionAspect). Seeding bypasses that entirely.
      String reportingId = seedReportingInTenant(tenantX, "Update Isolation Reporting");

      // Act - update from tenant Y
      ReportingInput hijack =
          createReportingInput("Hijacked name", ReportingContextType.PLATFORM, null);
      int responseStatus =
          mvc.perform(
                  put(tenantReportingsUri(tenantY) + "/" + reportingId)
                      .content(asJsonString(hijack))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Reporting created in tenant X should NOT be deletable from tenant Y")
    void given_reportingInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_REPORTINGS, Capability.ACCESS_REPORTINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_REPORTINGS, Capability.ACCESS_REPORTINGS));

      // Seeded directly (native insert): creating under tenant X's path via the API would set the
      // tenant scope (TxCtx) to X on this test's wrapping transaction, and the delete call below
      // sets it to Y - the aspect refuses a scope change within one transaction (see
      // TenantScopeTransactionAspect). Seeding bypasses that entirely.
      String reportingId = seedReportingInTenant(tenantX, "Delete Isolation Reporting");

      // Act - delete from tenant Y
      int responseStatus =
          mvc.perform(delete(tenantReportingsUri(tenantY) + "/" + reportingId).with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    private String tenantReportingsUri(Tenant tenant) {
      return TENANT_REPORTINGS_URI.replace("{tenantId}", tenant.getId());
    }

    /**
     * Seeds a reporting directly via native insert instead of the create endpoint: creating
     * through the API sets the tenant scope (TxCtx) on this test's wrapping transaction, which
     * conflicts with a subsequent call scoped to a different tenant within the same test (see
     * TenantScopeTransactionAspect).
     */
    private String seedReportingInTenant(Tenant tenant, String name) {
      String reportingId = UUID.randomUUID().toString();
      entityManager
          .createNativeQuery(
              "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type, tenant_id)"
                  + " VALUES (CAST(:id AS uuid), :name, :contextType, CAST(:tenant AS uuid))")
          .setParameter("id", reportingId)
          .setParameter("name", name)
          .setParameter("contextType", ReportingContextType.PLATFORM.name())
          .setParameter("tenant", tenant.getId())
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();
      return reportingId;
    }

    private String createReportingInTenant(Tenant tenant, String name) throws Exception {
      ReportingInput input = createReportingInput(name, ReportingContextType.PLATFORM, null);
      String createResponse =
          mvc.perform(
                  post(tenantReportingsUri(tenant))
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(createResponse, "$.reporting_id");
    }
  }
}
