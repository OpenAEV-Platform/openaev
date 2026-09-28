package io.openaev.rest.document;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * What an anonymous player of a non-default tenant reads, with {@code challenges} and {@code
 * documents} both v2-active. The caller is deliberately NOT {@code @WithMockUser}: that annotation
 * gives the request a membership set and resolves a scope the real player never has. A player
 * authenticates with the URL access token cookie alone, and {@code
 * TxCtxArgumentResolver#anonymousScope} then trusts the path tenant, or resolves {@code
 * TxCtx.missing()} when the route names none.
 *
 * <p>Three shapes reach these handlers, and the outcome differs for each:
 *
 * <ul>
 *   <li>the tenant the player belongs to in the path. This is what the platform emits: the mail
 *       links built in {@code ChallengeExecutor}, {@code ChannelExecutor} and {@code
 *       ExecutionContextService} put the simulation's tenant in the first path segment, the SPA
 *       turns it into the router basename and {@code buildTenantApiPath} prefixes every API call
 *       with it. Everything is visible.
 *   <li>the default tenant in the path, which is what the SPA falls back to when the browser URL
 *       carries no tenant segment. The simulation itself is resolved through the ambient tenant, so
 *       the page is refused before any document or challenge is read. This predates the activation
 *       of {@code documents}: the lookup is the v1 one and {@code exercises} is not active.
 *   <li>the non-prefixed route, which the SPA never produces and only a direct client reaches. No
 *       tenant is named, the scope is empty and an active table reads nothing. {@code challenges}
 *       accepted exactly this at its own activation, pinned by {@code
 *       SimulationChallengeApiPlayerLegacyRouteTest}.
 * </ul>
 *
 * <p>Not {@code @Transactional}: the rows are committed through an auto-committing {@link
 * JdbcTemplate} so every read of the request is a statement the inspector rewrites, and removed on
 * teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=challenges,documents")
@DisplayName("Anonymous player pages of a non-default tenant, challenges and documents active")
class PlayerDocumentsAnonymousRouteTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String SIMULATION_DOCUMENTS =
      "/api/tenants/{tenantId}/player/simulations/{simulationId}/documents";
  private static final String LEGACY_SIMULATION_DOCUMENTS =
      "/api/player/simulations/{simulationId}/documents";
  private static final String SIMULATION_CHALLENGES =
      "/api/tenants/{tenantId}/player/simulations/{simulationId}/challenges";
  private static final String LEGACY_SIMULATION_CHALLENGES =
      "/api/player/simulations/{simulationId}/challenges";
  private static final String PLAYER_DOCUMENTS =
      "/api/tenants/{tenantId}/player/{exerciseOrScenarioId}/documents";
  private static final String LEGACY_PLAYER_DOCUMENTS =
      "/api/player/{exerciseOrScenarioId}/documents";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String[]> seededRows = new ArrayList<>();

  private String tenantB;
  private String exerciseId;
  private String documentId;
  private String challengeId;
  private String rawToken;

  @BeforeEach
  void seedAPlayerPageInANonDefaultTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantB = seedTenant();
    exerciseId = seedExercise();
    documentId = seedDocumentPublishedToTheSimulation();
    String playerId = seedPlayerOfTheSimulation();
    challengeId = seedChallengeExpectation(playerId);
    rawToken = seedUrlAccessToken(playerId);
    // Production has no ambient tenant on the non-prefixed route, where it falls back to the
    // default one. Nothing in this class sets one, but a leftover would decide the outcome.
    TenantContext.clearCurrentTenant();
    assertEquals(
        DEFAULT_TENANT,
        TenantContext.getCurrentTenant(),
        "the ambient tenant must be the default one, as it is outside a tenant-prefixed request");
  }

  @AfterEach
  void cleanup() {
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("with the player's own tenant in the path, the shape the platform emits")
  class WithTheOwnTenantInThePath {

    @Test
    @DisplayName(
        "given an anonymous player of tenant B, when the media are listed, then they are visible")
    void given_anonymousPlayerOfTenantB_should_listTheSimulationMedia() throws Exception {
      // -- ACT & ASSERT --
      asPlayer(get(SIMULATION_DOCUMENTS, tenantB, exerciseId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].document_id", hasItem(documentId)));
      asPlayer(get(PLAYER_DOCUMENTS, tenantB, exerciseId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].document_id", hasItem(documentId)));
    }

    @Test
    @DisplayName(
        "given an anonymous player of tenant B, when the challenges are listed, then they are visible")
    void given_anonymousPlayerOfTenantB_should_listTheSimulationChallenges() throws Exception {
      // -- ACT & ASSERT --
      asPlayer(get(SIMULATION_CHALLENGES, tenantB, exerciseId))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath(
                  "$.exercise_challenges[*].challenge_detail.challenge_id", hasItem(challengeId)));
    }
  }

  @Nested
  @DisplayName("with the default tenant in the path, the SPA fallback when the URL names no tenant")
  class WithTheDefaultTenantInThePath {

    @Test
    @DisplayName(
        "given a simulation of tenant B, when the media are listed under the default tenant, then the page is refused")
    void given_simulationOfTenantB_should_refuseTheMediaUnderTheDefaultTenant() throws Exception {
      // -- ACT & ASSERT --
      asPlayer(get(SIMULATION_DOCUMENTS, DEFAULT_TENANT, exerciseId))
          .andExpect(status().isBadRequest());
      asPlayer(get(PLAYER_DOCUMENTS, DEFAULT_TENANT, exerciseId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a simulation of tenant B, when the challenges are listed under the default tenant, then none is visible")
    void given_simulationOfTenantB_should_showNoChallengeUnderTheDefaultTenant() throws Exception {
      // -- ACT & ASSERT --
      asPlayer(get(SIMULATION_CHALLENGES, DEFAULT_TENANT, exerciseId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_challenges").isEmpty());
    }
  }

  @Nested
  @DisplayName("on the non-prefixed route, which names no tenant at all")
  class OnTheNonPrefixedRoute {

    @Test
    @DisplayName(
        "given a simulation of tenant B, when the media are listed with no tenant named, then the page is refused")
    void given_simulationOfTenantB_should_refuseTheMediaWithNoTenantNamed() throws Exception {
      // -- ACT & ASSERT --
      asPlayer(get(LEGACY_SIMULATION_DOCUMENTS, exerciseId)).andExpect(status().isBadRequest());
      asPlayer(get(LEGACY_PLAYER_DOCUMENTS, exerciseId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a simulation of tenant B, when the challenges are listed with no tenant named, then none is visible")
    void given_simulationOfTenantB_should_showNoChallengeWithNoTenantNamed() throws Exception {
      // -- ACT & ASSERT --
      asPlayer(get(LEGACY_SIMULATION_CHALLENGES, exerciseId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_challenges").isEmpty());
    }
  }

  private org.springframework.test.web.servlet.ResultActions asPlayer(
      MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.cookie(new Cookie(URL_ACCESS_COOKIE_NAME, rawToken)));
  }

  // -- SEEDING --

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "player-anon-" + id);
    seededRows.add(new String[] {"tenants", "tenant_id", id});
    return id;
  }

  private String seedExercise() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "player-anon-" + id,
        tenantB);
    seededRows.add(new String[] {"exercises", "exercise_id", id});
    return id;
  }

  /** An article of a channel carrying the document, which publishes it to the players. */
  private String seedDocumentPublishedToTheSimulation() {
    String document = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'text/plain', ?, ?)",
        document,
        "player-anon-" + document,
        document + ".txt",
        tenantB);
    seededRows.add(new String[] {"documents", "document_id", document});
    String channel = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO channels (channel_id, channel_type, channel_name, tenant_id)"
            + " VALUES (?, 'newsletter', ?, ?)",
        channel,
        "player-anon-" + channel,
        tenantB);
    seededRows.add(new String[] {"channels", "channel_id", channel});
    String article = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO articles (article_id, article_channel, article_exercise, article_name)"
            + " VALUES (?, ?, ?, ?)",
        article,
        channel,
        exerciseId,
        "player-anon-" + article);
    seededRows.add(new String[] {"articles", "article_id", article});
    jdbc.update(
        "INSERT INTO articles_documents (article_id, document_id) VALUES (?, ?)",
        article,
        document);
    seededRows.add(new String[] {"articles_documents", "article_id", article});
    return document;
  }

  private String seedPlayerOfTheSimulation() {
    String user = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        user,
        "player-anon-" + user + "@openaev.io");
    seededRows.add(new String[] {"users", "user_id", user});
    String team = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        team,
        "player-anon-" + team,
        tenantB);
    seededRows.add(new String[] {"teams", "team_id", team});
    jdbc.update(
        "INSERT INTO exercises_teams (exercise_id, team_id) VALUES (?, ?)", exerciseId, team);
    seededRows.add(new String[] {"exercises_teams", "exercise_id", exerciseId});
    jdbc.update(
        "INSERT INTO exercises_teams_users (exercise_id, team_id, user_id) VALUES (?, ?, ?)",
        exerciseId,
        team,
        user);
    seededRows.add(new String[] {"exercises_teams_users", "exercise_id", exerciseId});
    return user;
  }

  private String seedChallengeExpectation(String playerId) {
    String challenge = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO challenges (challenge_id, challenge_name, tenant_id) VALUES (?, ?, ?)",
        challenge,
        "player-anon-" + challenge,
        tenantB);
    seededRows.add(new String[] {"challenges", "challenge_id", challenge});
    String inject = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, tenant_id)"
            + " VALUES (?, ?, false, true, 0, ?, ?)",
        inject,
        "player-anon-" + inject,
        exerciseId,
        tenantB);
    seededRows.add(new String[] {"injects", "inject_id", inject});
    String status = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name) VALUES (?, ?,"
            + " 'EXECUTED')",
        status,
        inject);
    seededRows.add(new String[] {"injects_statuses", "status_id", status});
    String expectation = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects_expectations (inject_expectation_id, inject_id, exercise_id, user_id,"
            + " challenge_id, inject_expectation_type, inject_expiration_time) VALUES (?, ?, ?, ?,"
            + " ?, 'CHALLENGE', 3600)",
        expectation,
        inject,
        exerciseId,
        playerId,
        challenge);
    seededRows.add(new String[] {"injects_expectations", "inject_expectation_id", expectation});
    return challenge;
  }

  private String seedUrlAccessToken(String playerId) {
    String raw = "player-anon-token-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO url_access_token (id, token_hash, url, user_id, exercise_id, expires_at)"
            + " VALUES (?, ?, ?, ?, ?, now() + interval '30 days')",
        UUID.randomUUID().toString(),
        sha256Hex(raw),
        "/api/player/simulations/" + exerciseId + "/documents",
        playerId,
        exerciseId);
    seededRows.add(new String[] {"url_access_token", "user_id", playerId});
    return raw;
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
