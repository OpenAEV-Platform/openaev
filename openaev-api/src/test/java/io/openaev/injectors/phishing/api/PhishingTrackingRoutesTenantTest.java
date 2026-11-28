package io.openaev.injectors.phishing.api;

import static io.openaev.injectors.phishing.api.HostedPublicApi.HOSTED_URI;
import static io.openaev.injectors.phishing.api.PhishingPublicApi.PHISHING_TRACKING_URI;
import static io.openaev.injectors.phishing.service.PhishingTrackingService.STEP_CLICKED;
import static io.openaev.injectors.phishing.service.PhishingTrackingService.STEP_OPENED;
import static io.openaev.injectors.phishing.service.PhishingTrackingService.STEP_SUBMITTED;
import static io.openaev.injectors.phishing.service.PhishingTrackingService.UNCONFIRMED_OPEN_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.Tenant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The public phishing routes an anonymous browser calls take the tenant of the tracking row from
 * the request, never from the ambient tenant: the {@code tenantId} path segment on the tracking
 * routes, the token's own tenant on the hosted routes. Each tenant holds a recipient player of a
 * team, the three step expectations of the player and of the team, a landing page that captures the
 * submitted data, and a second tracking row whose inject is to be read from its chaining
 * step. The ambient tenant of the test thread is cleared before each request and falls back to the
 * default tenant. No {@code @WithMockUser}: the token is the only authenticator.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
@DisplayName("Public phishing routes take the tenant of the tracking row from the request")
class PhishingTrackingRoutesTenantTest extends IntegrationTest {

  private static final String HUMAN_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64)";
  private static final String SCANNER_USER_AGENT = "Mozilla/5.0 (compatible; proofpoint)";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String CREDENTIALS = "{\"username\":\"bob\",\"password\":\"hunter2\"}";
  private static final List<String> STEPS = List.of(STEP_OPENED, STEP_CLICKED, STEP_SUBMITTED);
  private static final double RESISTED_SCORE = 100.0;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents events;

  private Seeded a;
  private Seeded b;

  @BeforeEach
  void seedOneRecipientPerTenant() {
    a = seedTenantWithRecipient("phishing-routes-a-");
    b = seedTenantWithRecipient("phishing-routes-b-");
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    for (Seeded seeded : List.of(a, b)) {
      jdbc.update("DELETE FROM findings WHERE tenant_id = ?", seeded.tenant());
      jdbc.update("DELETE FROM users WHERE user_id = ?", seeded.user());
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", seeded.tenant());
    }
  }

  @Nested
  @DisplayName("Tracking routes with the tenant in the path")
  class TrackingRoutes {

    @Test
    @DisplayName("given the open pixel of a token of B should annotate the open step in tenant B")
    void given_openPixelOfATokenOfB_should_annotateTheOpenStepInTenantB() throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act
      mvc.perform(
              tracking("/o/{token}", b.tenant(), b.token()).header("User-Agent", HUMAN_USER_AGENT))
          .andExpect(status().isOk())
          .andExpect(
              result ->
                  assertThat(result.getResponse().getContentType())
                      .isEqualTo(MediaType.IMAGE_GIF_VALUE));

      // Assert
      assertThat(playerResultMessages(b, STEP_OPENED)).containsExactly(UNCONFIRMED_OPEN_MESSAGE);
      assertThat(expectationEventTenants()).isNotEmpty().containsOnly(b.tenant());
    }

    @Test
    @DisplayName(
        "given the landing page of a chained token of B should read its inject in tenant B")
    void given_landingPageOfAChainedTokenOfB_should_readItsInjectInTenantB() throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act
      mvc.perform(get(PHISHING_TRACKING_URI + "/{tenantId}/page/{token}", b.tenant(), b.chained()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.phishing_landing_page_name").value(b.landingPageName()));

      // Assert
      assertThat(resultInject(b.chained())).isEqualTo(b.inject());
      assertThat(trackingEventTenants()).isNotEmpty().containsOnly(b.tenant());
    }

    @Test
    @DisplayName(
        "given a click, the landing page and a submission of B should score B, capture the"
            + " credentials in B and attribute the events to B")
    void given_fullFlowOfATokenOfB_should_scoreAndCaptureInTenantB() throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act
      mvc.perform(
              tracking("/c/{token}", b.tenant(), b.token()).header("User-Agent", HUMAN_USER_AGENT))
          .andExpect(status().isFound());
      assertAmbientTenantIsTheDefaultOne();
      mvc.perform(tracking("/page/{token}", b.tenant(), b.token())).andExpect(status().isOk());
      assertAmbientTenantIsTheDefaultOne();
      mvc.perform(
              post(PHISHING_TRACKING_URI + "/{tenantId}/s/{token}", b.tenant(), b.token())
                  .header("User-Agent", HUMAN_USER_AGENT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(CREDENTIALS))
          .andExpect(status().isOk());

      // Assert
      assertCompromisedAndCapturedInB();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"open", "click", "page", "submit"})
    @DisplayName("given a token of A under the path of B should touch no row")
    void given_tokenOfAUnderThePathOfB_should_touchNoRow(String route) throws Exception {
      // Arrange: the header names A too, which an anonymous request never selects with
      assertAmbientTenantIsTheDefaultOne();
      MockHttpServletRequestBuilder request =
          switch (route) {
            case "open" -> tracking("/o/{token}", b.tenant(), a.token());
            case "click" -> tracking("/c/{token}", b.tenant(), a.token());
            case "page" -> tracking("/page/{token}", b.tenant(), a.chained());
            default ->
                post(PHISHING_TRACKING_URI + "/{tenantId}/s/{token}", b.tenant(), a.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREDENTIALS);
          };

      // Act
      mvc.perform(
          request.header("User-Agent", HUMAN_USER_AGENT).header(TENANT_IDS_HEADER, a.tenant()));

      // Assert
      assertUntouched(a);
      assertThat(resultInject(a.chained())).isNull();
      assertThat(events.stream(BaseEvent.class)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Hosted routes with the token alone")
  class HostedRoutes {

    @Test
    @DisplayName("given the open pixel of a token of B should annotate the open step in tenant B")
    void given_openPixelOfATokenOfB_should_annotateTheOpenStepInTenantB() throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act
      mvc.perform(get(HOSTED_URI + "/o/{token}", b.token()).header("User-Agent", HUMAN_USER_AGENT))
          .andExpect(status().isOk());

      // Assert
      assertThat(playerResultMessages(b, STEP_OPENED)).containsExactly(UNCONFIRMED_OPEN_MESSAGE);
      assertThat(expectationEventTenants()).isNotEmpty().containsOnly(b.tenant());
      assertUntouched(a);
    }

    @Test
    @DisplayName(
        "given the landing page of a token of B fetched by a scanner should annotate the steps of"
            + " B and attribute the events to B")
    void given_landingPageFetchedByAScanner_should_annotateTheStepsOfB() throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act
      mvc.perform(
              get(HOSTED_URI + "/page/{token}", b.token()).header("User-Agent", SCANNER_USER_AGENT))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.phishing_landing_page_name").value(b.landingPageName()));

      // Assert
      assertThat(playerResultMessages(b, STEP_CLICKED)).hasSize(1);
      assertThat(playerScores(b)).containsOnly(RESISTED_SCORE);
      assertThat(expectationEventTenants()).isNotEmpty().containsOnly(b.tenant());
      assertUntouched(a);
    }

    @Test
    @DisplayName(
        "given the landing page, a confirmation and a submission of B should score B, capture"
            + " the credentials in B and attribute the events to B")
    void given_fullFlowOfATokenOfB_should_scoreAndCaptureInTenantB() throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act
      mvc.perform(
              get(HOSTED_URI + "/page/{token}", b.token()).header("User-Agent", HUMAN_USER_AGENT))
          .andExpect(status().isOk());
      assertAmbientTenantIsTheDefaultOne();
      mvc.perform(
              post(HOSTED_URI + "/confirm/{token}", b.token())
                  .header("User-Agent", HUMAN_USER_AGENT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"dwell_ms\":2300,\"webdriver\":false}"))
          .andExpect(status().isNoContent());
      assertAmbientTenantIsTheDefaultOne();
      mvc.perform(
              post(HOSTED_URI + "/s/{token}", b.token())
                  .header("User-Agent", HUMAN_USER_AGENT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(CREDENTIALS))
          .andExpect(status().isOk());

      // Assert
      assertCompromisedAndCapturedInB();
    }
  }

  // -- ASSERT HELPERS (raw JDBC: the inspector never rewrites it) --

  private void assertCompromisedAndCapturedInB() {
    assertThat(playerScores(b)).hasSize(STEPS.size()).containsOnly(0.0);
    assertThat(teamScores(b)).hasSize(STEPS.size()).containsOnly(0.0);
    assertThat(
            jdbc.queryForList(
                "SELECT f.tenant_id FROM phishing_results r"
                    + " JOIN findings f ON f.finding_id = r.phishing_result_finding"
                    + " WHERE r.phishing_result_token = ? AND f.finding_type = ?",
                String.class,
                b.token(),
                ContractOutputType.Credentials.name()))
        .containsExactly(b.tenant());
    assertThat(column(b.token(), "phishing_result_submitted_at")).isNotNull();
    assertThat(expectationEventTenants()).isNotEmpty().containsOnly(b.tenant());
    assertThat(trackingEventTenants()).isNotEmpty().containsOnly(b.tenant());
    assertUntouched(a);
  }

  private void assertUntouched(Seeded seeded) {
    assertThat(column(seeded.token(), "phishing_result_opened_at")).isNull();
    assertThat(column(seeded.token(), "phishing_result_clicked_at")).isNull();
    assertThat(column(seeded.token(), "phishing_result_submitted_at")).isNull();
    assertThat(playerScores(seeded)).containsOnly(RESISTED_SCORE);
    assertThat(playerResultMessages(seeded, STEP_OPENED)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM findings WHERE tenant_id = ?",
                Integer.class,
                seeded.tenant()))
        .isZero();
  }

  /** The tenant each event of a tracking row change of this request is attributed to. */
  private List<String> trackingEventTenants() {
    return eventTenants(PhishingResult.class);
  }

  /** The tenant each event of an expectation change of this request is attributed to. */
  private List<String> expectationEventTenants() {
    return eventTenants(BaseInjectExpectation.class);
  }

  private List<String> eventTenants(Class<?> type) {
    return events.stream(BaseEvent.class)
        .filter(event -> type.isInstance(event.getInstance()))
        .map(BaseEvent::getTenantId)
        .toList();
  }

  private Timestamp column(String token, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM phishing_results WHERE phishing_result_token = ?",
        Timestamp.class,
        token);
  }

  private String resultInject(String token) {
    return jdbc.queryForObject(
        "SELECT phishing_result_inject FROM phishing_results WHERE phishing_result_token = ?",
        String.class,
        token);
  }

  private List<Double> playerScores(Seeded seeded) {
    return jdbc.queryForList(
        "SELECT inject_expectation_score FROM injects_expectations"
            + " WHERE inject_id = ? AND user_id IS NOT NULL",
        Double.class,
        seeded.inject());
  }

  private List<Double> teamScores(Seeded seeded) {
    return jdbc.queryForList(
        "SELECT inject_expectation_score FROM injects_expectations"
            + " WHERE inject_id = ? AND user_id IS NULL",
        Double.class,
        seeded.inject());
  }

  private List<String> playerResultMessages(Seeded seeded, String step) {
    return jdbc.queryForList(
        "SELECT r.inject_expectation_result_result FROM inject_expectation_results r"
            + " JOIN injects_expectations e"
            + " ON e.inject_expectation_id = r.inject_expectation_result_inject_expectation_id"
            + " WHERE e.inject_id = ? AND e.user_id IS NOT NULL AND e.inject_expectation_name = ?",
        String.class,
        seeded.inject(),
        step);
  }

  private static MockHttpServletRequestBuilder tracking(
      String path, String tenantId, String token) {
    return get(PHISHING_TRACKING_URI + "/{tenantId}" + path, tenantId, token);
  }

  private static void assertAmbientTenantIsTheDefaultOne() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  // -- ARRANGE HELPERS (raw JDBC, committed: the tracking calls run in their own transactions) --

  /** The rows of one tenant: a direct tracking row and a chained one whose inject is unset. */
  record Seeded(
      String tenant,
      String inject,
      String user,
      String token,
      String chained,
      String landingPageName) {}

  private Seeded seedTenantWithRecipient(String prefix) {
    String tenant = insertTenant(prefix + UUID.randomUUID());
    String inject = insertInject(tenant);
    String user = insertUser(tenant);
    String team = insertTeam(tenant, user);
    for (String step : STEPS) {
      insertStepExpectation(inject, step, user, team);
      insertStepExpectation(inject, step, null, team);
    }
    String landingPage = UUID.randomUUID().toString();
    String landingPageName = "phishing-routes-landing-page-" + landingPage;
    jdbc.update(
        "INSERT INTO phishing_landing_pages (phishing_landing_page_id, tenant_id,"
            + " phishing_landing_page_name, phishing_landing_page_capture_submitted_data,"
            + " phishing_landing_page_capture_passwords, phishing_landing_page_created_at,"
            + " phishing_landing_page_updated_at) VALUES (?, ?, ?, true, false, now(), now())",
        landingPage,
        tenant,
        landingPageName);
    String token = "tok-routes-" + UUID.randomUUID();
    insertTrackingRow(tenant, token, inject, null, user, team, landingPage);
    String chained = "tok-routes-chained-" + UUID.randomUUID();
    insertTrackingRow(tenant, chained, null, insertStep(tenant, inject), user, team, landingPage);
    return new Seeded(tenant, inject, user, token, chained, landingPageName);
  }

  private String insertTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private String insertInject(String tenant) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams,"
            + " inject_enabled, inject_depends_duration) VALUES (?, ?, ?, false, true, 0)",
        id,
        tenant,
        "phishing-routes-inject-" + id);
    return id;
  }

  private String insertUser(String tenant) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status)"
            + " VALUES (?, ?, false, 1)",
        id,
        "phishing-routes-" + id + "@openaev.test");
    jdbc.update("INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?)", id, tenant);
    return id;
  }

  private String insertTeam(String tenant, String user) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "phishing-routes-team-" + id,
        tenant);
    jdbc.update("INSERT INTO users_teams (team_id, user_id) VALUES (?, ?)", id, user);
    return id;
  }

  private void insertStepExpectation(String inject, String step, String user, String team) {
    jdbc.update(
        "INSERT INTO injects_expectations (inject_expectation_id, inject_id, user_id, team_id,"
            + " inject_expectation_type, inject_expectation_name, inject_expectation_score,"
            + " inject_expectation_expected_score, inject_expiration_time)"
            + " VALUES (?, ?, ?, ?, 'MANUAL', ?, ?, ?, 86400)",
        UUID.randomUUID().toString(),
        inject,
        user,
        team,
        step,
        RESISTED_SCORE,
        RESISTED_SCORE);
  }

  /** A running chained step of a simulation of the tenant, whose data names the inject. */
  private String insertStep(String tenant, String inject) {
    String exercise = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, tenant_id) VALUES (?, ?, ?)",
        exercise,
        "phishing-routes-simulation-" + exercise,
        tenant);
    String workflow = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version,"
            + " workflow_simulation_id, tenant_id) VALUES (?, 'RUN', 0, ?, ?)",
        workflow,
        exercise,
        tenant);
    String step = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO steps (step_id, step_action_class, step_data, step_limit_execution,"
            + " step_status, step_workflow_id, tenant_id)"
            + " VALUES (?, 'INJECT_EXECUTION', ?::jsonb, 0, 'RUN', ?, ?)",
        step,
        "{\"inject_id\":\"" + inject + "\"}",
        workflow,
        tenant);
    return step;
  }

  /** Sent a day ago: a hit within seconds of the send counts as an automated probe. */
  private void insertTrackingRow(
      String tenant,
      String token,
      String inject,
      String step,
      String user,
      String team,
      String landingPage) {
    jdbc.update(
        "INSERT INTO phishing_results (phishing_result_id, tenant_id, phishing_result_token,"
            + " phishing_result_inject, phishing_result_step, phishing_result_user,"
            + " phishing_result_team, phishing_result_landing_page, phishing_result_sent_at,"
            + " phishing_result_created_at, phishing_result_updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, now() - interval '1 day', now(), now())",
        UUID.randomUUID().toString(),
        tenant,
        token,
        inject,
        step,
        user,
        team,
        landingPage);
  }
}
