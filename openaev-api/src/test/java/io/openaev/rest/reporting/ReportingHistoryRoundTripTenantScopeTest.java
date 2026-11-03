package io.openaev.rest.reporting;

import static io.openaev.rest.reporting.ReportingApi.TENANT_REPORTINGS_URI;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.ReportingGenerationTrigger;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.database.repository.ReportingRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ReportingFixture;
import io.openaev.utils.fixtures.composers.ReportingComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The reporting endpoints return the raw {@code Reporting} entity. Its lazy {@code
 * reporting_generations} and {@code reporting_schedules}, serialized open-in-view after the scoped
 * transaction has committed, fail closed to an empty array. Each test reads a reporting carrying
 * two generations and a schedule, sends it back to the update endpoint as it came, and checks the
 * history in the response and in the database.
 *
 * <p>The tests run with the production list of isolated tables. The class is deliberately NOT
 * {@code @Transactional}: a rolled-back test transaction never commits, so the tenant-local scope
 * would stay alive through serialization and mask the failure. Seeding and cleanup run in committed
 * transactions scoped to the test tenant, and the database checks read the tables directly, outside
 * any scope.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Reporting endpoints keep their history across a read and an unchanged save")
class ReportingHistoryRoundTripTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ReportingComposer reportingComposer;
  @Autowired private ReportingRepository reportingRepository;
  @Autowired private ReportingGenerationRepository reportingGenerationRepository;

  private JdbcTemplate jdbc;
  private String tenant;

  @BeforeEach
  void setUp() throws Exception {
    reportingComposer.reset();
    jdbc = new JdbcTemplate(dataSource);
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("reporting-round-trip").getId();
  }

  @AfterEach
  void cleanup() {
    inTenantScope(
        () -> {
          // Deleting the reporting removes its generations and schedules through the cascade.
          reportingRepository.deleteAllById(
              reportingComposer.generatedItems.stream().map(Reporting::getId).toList());
          // The helper's deletes on collectors and collector_types (isolated in production) join
          // this transaction, so they run with the tenant in scope.
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("Read a reporting by id")
  class ReadById {

    @Test
    @DisplayName(
        "given a reporting with history should return and keep it when the read reporting is"
            + " saved")
    void given_reportingWithHistory_should_returnAndKeepIt_when_readReportingIsSaved()
        throws Exception {
      // -- Arrange --
      Seeded seeded = seedReportingWithHistory();
      SoftAssertions softly = new SoftAssertions();

      // -- Act --
      JsonNode read =
          readJson(
              mvc.perform(
                  get(TENANT_REPORTINGS_URI + "/{reportingId}", tenant, seeded.reportingId())
                      .with(csrf())));
      assertHistory(softly, read, seeded, "read response");
      JsonNode saved = saveUnchanged(seeded, read);
      assertHistory(softly, saved, seeded, "update response");

      // -- Assert --
      assertHistoryInDatabase(softly, seeded);
      softly.assertAll();
    }
  }

  @Nested
  @DisplayName("Search and list reportings")
  class SearchAndList {

    @Test
    @DisplayName(
        "given a reporting with history should return and keep it when the searched reporting is"
            + " saved")
    void given_reportingWithHistory_should_returnAndKeepIt_when_searchedReportingIsSaved()
        throws Exception {
      // -- Arrange --
      Seeded seeded = seedReportingWithHistory();
      SoftAssertions softly = new SoftAssertions();

      // -- Act --
      JsonNode searched =
          findById(
              readJson(
                      mvc.perform(
                          post(TENANT_REPORTINGS_URI + "/search", tenant)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  mapper.writeValueAsString(
                                      PaginationFixture.simpleTextSearch(seeded.name())))
                              .with(csrf())))
                  .get("content"),
              seeded.reportingId());
      assertHistory(softly, searched, seeded, "search response");
      saveUnchanged(seeded, searched);

      // -- Assert --
      assertHistoryInDatabase(softly, seeded);
      softly.assertAll();
    }

    @Test
    @DisplayName("given a reporting with history should return it in the list of its subject")
    void given_reportingWithHistory_should_returnIt_when_listedBySubject() throws Exception {
      // -- Arrange --
      Seeded seeded = seedReportingWithHistory();
      SoftAssertions softly = new SoftAssertions();

      // -- Act --
      JsonNode listed =
          findById(
              readJson(
                  mvc.perform(
                      get(TENANT_REPORTINGS_URI + "/context/{contextType}", tenant, "PLATFORM")
                          .with(csrf()))),
              seeded.reportingId());

      // -- Assert --
      assertHistory(softly, listed, seeded, "subject list response");
      softly.assertAll();
    }
  }

  // -- SEEDING --

  /** The ids of a seeded reporting and its history, generations newest first. */
  private record Seeded(
      String reportingId, String name, List<String> generationIds, String scheduleId) {}

  /**
   * A reporting with two generations committed one after the other, so their creation times differ,
   * and a schedule created through the real endpoint.
   */
  private Seeded seedReportingWithHistory() throws Exception {
    Reporting reporting = ReportingFixture.createDefaultReporting();
    reporting.setName("round-trip-reporting-" + UUID.randomUUID());
    reporting.setTenant(new Tenant(tenant));
    String reportingId =
        inTenantScope(() -> reportingComposer.forReporting(reporting).persist().get().getId());
    String older = seedGeneration(reportingId);
    String newer = seedGeneration(reportingId);
    String scheduleId =
        readJson(
                mvc.perform(
                    post(TENANT_REPORTINGS_URI + "/{reportingId}/schedules", tenant, reportingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            mapper.writeValueAsString(
                                ReportingFixture.createDailyScheduleInput(
                                    "round-trip-schedule", "08:00")))
                        .with(csrf())))
            .get("reporting_schedule_id")
            .asText();
    return new Seeded(reportingId, reporting.getName(), List.of(newer, older), scheduleId);
  }

  private String seedGeneration(String reportingId) {
    return inTenantScope(
        () -> {
          ReportingGeneration generation = new ReportingGeneration();
          generation.setReporting(reportingRepository.findById(reportingId).orElseThrow());
          generation.setFormat(ReportingFormat.PDF);
          generation.setGenerationTrigger(ReportingGenerationTrigger.MANUAL);
          generation.setStatus(ReportingGenerationStatus.PENDING);
          generation.setTenant(new Tenant(tenant));
          return reportingGenerationRepository.save(generation).getId();
        });
  }

  private <T> T inTenantScope(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenant), work);
  }

  // -- CALLS --

  /** Sends the reporting back through the update endpoint exactly as a read returned it. */
  private JsonNode saveUnchanged(Seeded seeded, JsonNode readReporting) throws Exception {
    return readJson(
        mvc.perform(
            put(TENANT_REPORTINGS_URI + "/{reportingId}", tenant, seeded.reportingId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(readReporting))
                .with(csrf())));
  }

  private JsonNode readJson(ResultActions call) throws Exception {
    return mapper.readTree(
        call.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private static JsonNode findById(JsonNode reportings, String reportingId) {
    return StreamSupport.stream(reportings.spliterator(), false)
        .filter(node -> reportingId.equals(node.get("reporting_id").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("reporting " + reportingId + " not returned"));
  }

  // -- CHECKS --

  /** The generations come back newest first, as the {@code @OrderBy} of the association says. */
  private static void assertHistory(
      SoftAssertions softly, JsonNode reporting, Seeded seeded, String where) {
    softly
        .assertThat(idsOf(reporting.get("reporting_generations"), "reporting_generation_id"))
        .as("generations in the " + where)
        .containsExactlyElementsOf(seeded.generationIds());
    softly
        .assertThat(idsOf(reporting.get("reporting_schedules"), "reporting_schedule_id"))
        .as("schedules in the " + where)
        .containsExactly(seeded.scheduleId());
  }

  private void assertHistoryInDatabase(SoftAssertions softly, Seeded seeded) {
    softly
        .assertThat(
            jdbc.queryForList(
                "SELECT reporting_generation_id FROM reporting_generations WHERE reporting_id = ?",
                String.class,
                seeded.reportingId()))
        .as("generations after saving")
        .containsExactlyInAnyOrderElementsOf(seeded.generationIds());
    softly
        .assertThat(
            jdbc.queryForList(
                "SELECT reporting_schedule_id FROM reporting_schedules WHERE reporting_id = ?",
                String.class,
                seeded.reportingId()))
        .as("schedules after saving")
        .containsExactly(seeded.scheduleId());
  }

  private static List<String> idsOf(JsonNode array, String idField) {
    return StreamSupport.stream(array.spliterator(), false)
        .map(node -> node.get(idField).asText())
        .toList();
  }
}
