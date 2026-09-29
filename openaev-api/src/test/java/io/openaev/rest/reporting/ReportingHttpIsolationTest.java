package io.openaev.rest.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code reportings}, {@code reporting_generations} and {@code
 * reporting_schedules} activated, the tenant scope isolates the three tables through the real
 * {@link ReportingApi} endpoints, on both routes: the tenant path ({@code
 * /api/tenants/{tenantId}/reportings}) and the {@code X-Tenant-Ids} header. Reads (primary-key
 * loads included) are scoped by {@code TenantStatementInspector}; a reporting is attributed from
 * the request's write scope, a generation or a schedule inherits its parent reporting's tenant.
 *
 * <p>Ground truth is read with raw JDBC on the test's own connection, which the statement inspector
 * never rewrites, so a leaked request scope cannot mask an assertion. The render pipeline ({@code
 * PlaywrightReportingRenderer}) is never exercised here: generations and schedules are seeded
 * directly, and only endpoints that do not dispatch a render are called.
 */
@Transactional
@TestPropertySource(
    properties =
        "openaev.tenant.active-tables=reportings,reporting_generations,reporting_schedules")
@WithMockUser(isAdmin = true)
@DisplayName(
    "reportings, reporting_generations and reporting_schedules isolation through the real HTTP"
        + " endpoints, both routes")
class ReportingHttpIsolationTest extends IntegrationTest {

  private static final String REPORTINGS = "/api/reportings";
  private static final String TENANT_REPORTINGS = "/api/tenants/{tenantId}/reportings";
  private static final String REPORTING_BY_ID = "/api/tenants/{tenantId}/reportings/{reportingId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String reportingA;
  private String reportingB;
  private String generationA;
  private String generationB;

  @BeforeEach
  void seedTwoTenantsWithOneReportingEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("reporting-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("reporting-http-iso-b").getId();
    reportingA = seedReporting(tenantA, "reporting-a");
    reportingB = seedReporting(tenantB, "reporting-b");
    generationA = seedGeneration(tenantA, reportingA);
    generationB = seedGeneration(tenantB, reportingB);
  }

  // -- REPORTINGS: READS BY ID, both routes --

  @Test
  @DisplayName("under tenant A's path: A's reporting is visible, B's is not found")
  void given_reportingsUnderTenantAPath_should_exposeOnlyAReporting() throws Exception {
    mvc.perform(get(REPORTING_BY_ID, tenantA, reportingA)).andExpect(status().isOk());
    mvc.perform(get(REPORTING_BY_ID, tenantA, reportingB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: A's reporting is visible, B's is not found")
  void given_reportingsViaHeaderA_should_exposeOnlyAReporting() throws Exception {
    mvc.perform(get(REPORTINGS + "/{reportingId}", reportingA).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk());
    mvc.perform(get(REPORTINGS + "/{reportingId}", reportingB).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isNotFound());
  }

  // -- REPORTINGS: SEARCH, both routes --

  @Test
  @DisplayName("under tenant A's path: search returns A's reporting and not B's")
  void given_searchUnderTenantAPath_should_returnOnlyAReporting() throws Exception {
    String response =
        mvc.perform(
                post(TENANT_REPORTINGS + "/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(reportingA), "A's reporting must appear in A's search results");
    assertFalse(response.contains(reportingB), "B's reporting must not appear in A's results");
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: search returns A's reporting and not B's")
  void given_searchViaHeaderA_should_returnOnlyAReporting() throws Exception {
    String response =
        mvc.perform(
                post(REPORTINGS + "/search")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(reportingA), "A's reporting must appear when A is selected");
    assertFalse(response.contains(reportingB), "B's reporting must not appear");
  }

  // -- REPORTINGS: CREATE ATTRIBUTION, both routes --

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void given_createUnderTenantAPath_should_attributeRowToA() throws Exception {
    String id = createReporting(TENANT_REPORTINGS, tenantA, null);
    assertEquals(tenantA, rawColumn("reportings", "reporting_id", id, "tenant_id"));
  }

  @Test
  @DisplayName("a create via the X-Tenant-Ids header is attributed to the header tenant")
  void given_createViaHeaderA_should_attributeRowToA() throws Exception {
    String id = createReporting(REPORTINGS, null, tenantA);
    assertEquals(tenantA, rawColumn("reportings", "reporting_id", id, "tenant_id"));
  }

  // -- REPORTINGS: UPDATE / DELETE cross-tenant, both routes, with ground-truth checks --

  @Test
  @DisplayName("under tenant A's path: updating B's reporting is not found and leaves it untouched")
  void given_updateUnderTenantAPathOfBReporting_should_return404AndKeepName() throws Exception {
    mvc.perform(
            put(REPORTING_BY_ID, tenantA, reportingB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"reporting_name\":\"hijacked\",\"reporting_context_type\":\"PLATFORM\"}")
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(
        "reporting-b",
        rawColumn("reportings", "reporting_id", reportingB, "reporting_name"),
        "B's reporting must be untouched by tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's reporting is not found and leaves it in place")
  void given_deleteUnderTenantAPathOfBReporting_should_return404AndKeepRow() throws Exception {
    mvc.perform(delete(REPORTING_BY_ID, tenantA, reportingB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount("reportings", "reporting_id", reportingB));
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: deleting B's reporting is not found and leaves it")
  void given_deleteViaHeaderAOfBReporting_should_return404AndKeepRow() throws Exception {
    mvc.perform(
            delete(REPORTINGS + "/{reportingId}", reportingB)
                .header("X-Tenant-Ids", tenantA)
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount("reportings", "reporting_id", reportingB));
  }

  @Test
  @DisplayName("under tenant A's path: A deletes its own reporting, cascading its generation")
  void given_deleteUnderTenantAPathOfOwnReporting_should_removeRowAndCascade() throws Exception {
    mvc.perform(delete(REPORTING_BY_ID, tenantA, reportingA).with(csrf()))
        .andExpect(status().isNoContent());
    assertEquals(0L, rawCount("reportings", "reporting_id", reportingA));
    assertEquals(0L, rawCount("reporting_generations", "reporting_generation_id", generationA));
  }

  // -- GENERATIONS: reached only through the parent reporting's scope --

  @Test
  @DisplayName("under tenant A's path: A's generation is visible, B's is not found")
  void given_generationsUnderTenantAPath_should_exposeOnlyAGeneration() throws Exception {
    mvc.perform(
            get(
                "/api/tenants/{tenantId}/reportings/generations/{generationId}",
                tenantA,
                generationA))
        .andExpect(status().isOk());
    mvc.perform(
            get(
                "/api/tenants/{tenantId}/reportings/generations/{generationId}",
                tenantA,
                generationB))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: A's generation is visible, B's is not found")
  void given_generationsViaHeaderA_should_exposeOnlyAGeneration() throws Exception {
    mvc.perform(
            get("/api/reportings/generations/{generationId}", generationA)
                .header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/reportings/generations/{generationId}", generationB)
                .header("X-Tenant-Ids", tenantA))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: listing B's reporting's generations is not found")
  void given_listGenerationsOfBReportingUnderTenantAPath_should_return404() throws Exception {
    mvc.perform(get(REPORTING_BY_ID + "/generations", tenantA, reportingB))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's generation is not found and leaves it in place")
  void given_deleteGenerationUnderTenantAPathOfB_should_return404AndKeepRow() throws Exception {
    mvc.perform(
            delete(
                    "/api/tenants/{tenantId}/reportings/generations/{generationId}",
                    tenantA,
                    generationB)
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount("reporting_generations", "reporting_generation_id", generationB));
  }

  // -- SCHEDULES: created under the parent reporting's tenant, both routes --

  @Test
  @DisplayName("a schedule created under tenant A's path inherits A's reporting's tenant")
  void given_createScheduleUnderTenantAPath_should_inheritReportingTenant() throws Exception {
    String response =
        mvc.perform(
                post(REPORTING_BY_ID + "/schedules", tenantA, reportingA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reporting_schedule_period\":\"DAY\"}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String scheduleId = JsonPath.read(response, "$.reporting_schedule_id");
    assertEquals(
        tenantA,
        rawColumn("reporting_schedules", "reporting_schedule_id", scheduleId, "tenant_id"));
  }

  @Test
  @DisplayName("a schedule created via the X-Tenant-Ids header inherits the reporting's tenant")
  void given_createScheduleViaHeaderA_should_inheritReportingTenant() throws Exception {
    String response =
        mvc.perform(
                post(REPORTINGS + "/{reportingId}/schedules", reportingA)
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reporting_schedule_period\":\"DAY\"}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String scheduleId = JsonPath.read(response, "$.reporting_schedule_id");
    assertEquals(
        tenantA,
        rawColumn("reporting_schedules", "reporting_schedule_id", scheduleId, "tenant_id"));
  }

  @Test
  @DisplayName("under tenant A's path: creating a schedule on B's reporting is not found")
  void given_createScheduleOnBReportingUnderTenantAPath_should_return404() throws Exception {
    mvc.perform(
            post(REPORTING_BY_ID + "/schedules", tenantA, reportingB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reporting_schedule_period\":\"DAY\"}")
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  // -- helpers --

  private String createReporting(String uriTemplate, String pathTenant, String headerTenant)
      throws Exception {
    var request = pathTenant != null ? post(uriTemplate, pathTenant) : post(uriTemplate);
    if (headerTenant != null) {
      request = request.header("X-Tenant-Ids", headerTenant);
    }
    String response =
        mvc.perform(
                request
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"reporting_name\":\"created\",\"reporting_context_type\":\"PLATFORM\"}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.reporting_id");
  }

  /**
   * Seeds a minimal reporting with a native {@code INSERT ... VALUES} (the inspector does not block
   * VALUES inserts). Raw JDBC rather than {@code entityManager.persist} so the request's {@code
   * findById} is never a first-level-cache hit that bypasses the inspector.
   */
  private String seedReporting(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "INSERT INTO reportings (reporting_id, reporting_name,"
                          + " reporting_context_type, tenant_id) VALUES (?, ?, 'PLATFORM', ?)")) {
                statement.setString(1, id);
                statement.setString(2, name);
                statement.setString(3, tenantId);
                statement.executeUpdate();
              }
            });
    return id;
  }

  private String seedGeneration(String tenantId, String reportingId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "INSERT INTO reporting_generations (reporting_generation_id, reporting_id,"
                          + " reporting_generation_format, tenant_id) VALUES (?, ?, 'PDF', ?)")) {
                statement.setString(1, id);
                statement.setString(2, reportingId);
                statement.setString(3, tenantId);
                statement.executeUpdate();
              }
            });
    return id;
  }

  private String rawColumn(String table, String idColumn, String id, String column) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT " + column + " FROM " + table + " WHERE " + idColumn + " = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                  return resultSet.next() ? resultSet.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String table, String idColumn, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM " + table + " WHERE " + idColumn + " = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                  resultSet.next();
                  return resultSet.getLong(1);
                }
              }
            });
  }
}
