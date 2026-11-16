package io.openaev.rest.reporting;

import static io.openaev.rest.reporting.ReportingApi.TENANT_REPORTINGS_URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolation of {@code reporting_generations} and {@code reporting_schedules} on their own rows,
 * through the real {@link ReportingApi} endpoints on the tenant path.
 *
 * <p>Every generation and schedule hangs off a reporting, and a reporting of another tenant is
 * already refused on {@code reportings}, so a case built on another tenant's reporting cannot tell
 * which table refused. Here the other tenant's generation and schedule are attached to the caller's
 * own reporting: the parent is visible, the subject check passes, and only the child table's own
 * scope stands between the caller and the row. The seed is raw JDBC with an explicit tenant, which
 * the statement inspector never rewrites and which no endpoint would produce.
 *
 * <p>Each test stays on tenant A's path, so the per-request scope is the same for every request.
 */
@CoversTenantIsolation({"reporting_generations", "reporting_schedules"})
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("reporting_generations and reporting_schedules refuse another tenant's row by id")
class ReportingChildRowHttpIsolationTest extends IntegrationTest {

  private static final String REPORTING_BY_ID = TENANT_REPORTINGS_URI + "/{reportingId}";
  private static final String GENERATION_BY_ID =
      TENANT_REPORTINGS_URI + "/generations/{generationId}";
  private static final String SCHEDULE_BY_ID = REPORTING_BY_ID + "/schedules/{scheduleId}";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String reportingA;

  @BeforeEach
  void seedTwoTenantsAndOneReportingInA() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("reporting-child-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("reporting-child-iso-b").getId();
    reportingA = seedReporting(tenantA);
  }

  @Nested
  @DisplayName("reporting_generations")
  class Generations {

    private String ownGeneration;
    private String foreignGeneration;

    @BeforeEach
    void seedOneGenerationPerTenantOnTheSameReporting() {
      ownGeneration = seedGeneration(tenantA);
      foreignGeneration = seedGeneration(tenantB);
    }

    @Test
    @DisplayName("given its own generation, should read it")
    void given_ownGeneration_should_readIt() throws Exception {
      // Act & Assert
      mvc.perform(get(GENERATION_BY_ID, tenantA, ownGeneration))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reporting_generation_id").value(ownGeneration));
    }

    @Test
    @DisplayName("given another tenant's generation on its own reporting, should not find it")
    void given_otherTenantGeneration_should_notFindIt() throws Exception {
      // Act & Assert
      mvc.perform(get(GENERATION_BY_ID, tenantA, foreignGeneration))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given its reporting's generations, should list its own and not the other's")
    void given_reportingGenerations_should_listOnlyItsOwn() throws Exception {
      // Act
      String response =
          mvc.perform(get(REPORTING_BY_ID + "/generations", tenantA, reportingA))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertTrue(response.contains(ownGeneration), "A's generation must be listed");
      assertFalse(response.contains(foreignGeneration), "B's generation must not be listed");
    }

    @Test
    @DisplayName("given another tenant's generation, should refuse its delete and keep the row")
    void given_otherTenantGeneration_should_refuseDeleteAndKeepRow() throws Exception {
      // Act
      mvc.perform(delete(GENERATION_BY_ID, tenantA, foreignGeneration).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(
          1L,
          rawLong(
              "SELECT count(*) FROM reporting_generations WHERE reporting_generation_id = ?",
              foreignGeneration),
          "B's generation must survive tenant A's delete");
    }
  }

  @Nested
  @DisplayName("reporting_schedules")
  class Schedules {

    private String ownSchedule;
    private String foreignSchedule;

    @BeforeEach
    void seedOneSchedulePerTenantOnTheSameReporting() {
      ownSchedule = seedSchedule(tenantA, "schedule-a");
      foreignSchedule = seedSchedule(tenantB, "schedule-b");
    }

    @Test
    @DisplayName("given its own schedule, should update it")
    void given_ownSchedule_should_updateIt() throws Exception {
      // Act
      mvc.perform(updateSchedule(ownSchedule)).andExpect(status().isOk());

      // Assert
      assertEquals("renamed", rawScheduleName(ownSchedule));
    }

    @Test
    @DisplayName("given another tenant's schedule on its own reporting, should refuse its update")
    void given_otherTenantSchedule_should_refuseUpdateAndKeepName() throws Exception {
      // Act
      mvc.perform(updateSchedule(foreignSchedule)).andExpect(status().isNotFound());

      // Assert
      assertEquals(
          "schedule-b", rawScheduleName(foreignSchedule), "B's schedule must be untouched");
    }

    @Test
    @DisplayName("given another tenant's schedule on its own reporting, should refuse its delete")
    void given_otherTenantSchedule_should_refuseDeleteAndKeepRow() throws Exception {
      // Act
      mvc.perform(delete(SCHEDULE_BY_ID, tenantA, reportingA, foreignSchedule).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(
          1L,
          rawLong(
              "SELECT count(*) FROM reporting_schedules WHERE reporting_schedule_id = ?",
              foreignSchedule),
          "B's schedule must survive tenant A's delete");
    }

    private RequestBuilder updateSchedule(String scheduleId) {
      return put(SCHEDULE_BY_ID, tenantA, reportingA, scheduleId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
              "{\"reporting_schedule_name\":\"renamed\",\"reporting_schedule_period\":\"DAY\","
                  + "\"reporting_schedule_time\":\"09:00\"}")
          .with(csrf());
    }
  }

  // -- FIXTURES --

  private String seedReporting(String tenantId) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type, tenant_id)"
            + " VALUES (?, 'reporting-a', 'PLATFORM', ?)",
        id,
        tenantId);
    return id;
  }

  private String seedGeneration(String tenantId) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO reporting_generations (reporting_generation_id, reporting_id,"
            + " reporting_generation_format, tenant_id) VALUES (?, ?, 'PDF', ?)",
        id,
        reportingA,
        tenantId);
    return id;
  }

  private String seedSchedule(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO reporting_schedules (reporting_schedule_id, reporting_id,"
            + " reporting_schedule_name, reporting_schedule_period, user_id, tenant_id)"
            + " VALUES (?, ?, ?, 'DAY', ?, ?)",
        id,
        reportingA,
        name,
        testUserHolder.get().getId(),
        tenantId);
    return id;
  }

  // Ground truth, scope-free: raw JDBC on the test's own connection sees the uncommitted seed and
  // the inspector never rewrites a statement it did not generate.
  private String rawScheduleName(String scheduleId) {
    return rawString(
        "SELECT reporting_schedule_name FROM reporting_schedules WHERE reporting_schedule_id = ?",
        scheduleId);
  }

  private void execute(String sql, String... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                statement.executeUpdate();
              }
            });
  }

  private String rawString(String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawLong(String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
