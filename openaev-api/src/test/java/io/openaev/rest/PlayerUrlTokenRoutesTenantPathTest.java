package io.openaev.rest;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.openaev.IntegrationTest;
import io.openaev.config.WaivesUnscopedReads;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.service.FileService;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Which tenant an anonymous player's url access token route reads its simulation in, for the player
 * routes whose three route shapes no other class pins. Each route is mapped twice, under the tenant
 * prefix and on the non-prefixed {@code /api/player/...} route.
 *
 * <ul>
 *   <li>The simulation's tenant in the path is what the platform emits: every player link (lessons,
 *       challenges, channels, player page) carries the simulation's tenant as its first segment,
 *       and the SPA prefixes every API call with it ({@code buildTenantApiPath}).
 *   <li>Another tenant in the path reads the simulation in that tenant, where it does not exist.
 *   <li>The non-prefixed route names no tenant: {@code TxCtxArgumentResolver#anonymousScope}
 *       resolves {@code TxCtx.missing()}, so nothing of the simulation's tenant is read. Nothing
 *       the platform emits takes this route.
 * </ul>
 *
 * <p>The documents and challenges lists of a simulation are pinned the same way by {@code
 * PlayerDocumentsAnonymousRouteTest}, the lessons answers by {@code
 * PlayerLessonsAnonymousScopeTest}.
 *
 * <p>Not {@code @Transactional}: the rows are committed through an auto-committing {@link
 * JdbcTemplate} so every read of the request is a statement of its own transaction, and removed on
 * teardown.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Anonymous player url access token routes read the simulation of the path tenant")
class PlayerUrlTokenRoutesTenantPathTest extends IntegrationTest {

  private static final String FILE_CONTENT = "player-route-file";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private FileService fileService;

  private JdbcTemplate jdbc;
  private final List<String[]> seededRows = new ArrayList<>();

  private String tenantB;
  private String exerciseId;
  private String scenarioId;
  private String channelId;
  private String documentId;
  private String documentTarget;
  private String challengeId;
  private String categoryId;
  private String questionId;
  private String teamOfThePlayer;
  private String rawToken;

  /**
   * One url access token route: its path below {@code /api} or {@code /api/tenants/{tenantId}}, its
   * body when it is a POST, the seeded id the page it returns must carry, and the status it answers
   * when the simulation is not in the request's scope.
   */
  record PlayerRoute(
      String name, HttpMethod method, String path, String body, String shownId, int refusal) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<PlayerRoute> playerRoutes() {
    return Stream.of(
        new PlayerRoute(
            "player page", HttpMethod.GET, "/player/exercises/{exercise}", null, "{exercise}", 404),
        new PlayerRoute(
            "channel articles",
            HttpMethod.GET,
            "/player/channels/{exercise}/{channel}",
            null,
            "{channel}",
            404),
        new PlayerRoute(
            "scenario documents",
            HttpMethod.GET,
            "/player/scenarios/{scenario}/documents",
            null,
            "{document}",
            400),
        new PlayerRoute(
            "document file",
            HttpMethod.GET,
            "/player/{exercise}/documents/{document}/file",
            null,
            FILE_CONTENT,
            404),
        new PlayerRoute(
            "challenge validation",
            HttpMethod.POST,
            "/player/challenges/{exercise}/{challenge}/validate",
            "{\"challenge_value\":\"wrong\"}",
            "{challenge}",
            404),
        new PlayerRoute(
            "lessons categories",
            HttpMethod.GET,
            "/player/lessons/exercise/{exercise}/lessons_categories",
            null,
            "{category}",
            404),
        new PlayerRoute(
            "lessons questions",
            HttpMethod.GET,
            "/player/lessons/exercise/{exercise}/lessons_questions",
            null,
            "{question}",
            404),
        new PlayerRoute(
            "lessons answer",
            HttpMethod.POST,
            "/player/lessons/exercise/{exercise}/lessons_categories/{category}/lessons_questions/{question}/lessons_answers",
            "{\"lessons_answer_positive\":\"kept\",\"lessons_answer_negative\":\"changed\","
                + "\"lessons_answer_score\":5}",
            "{question}",
            404));
  }

  @BeforeEach
  void seedASimulationOfTenantBWithAPlayerToken() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantB = seedTenant();
    exerciseId = seedRow("exercises", "exercise_id", "exercise_name");
    scenarioId = seedRow("scenarios", "scenario_id", "scenario_name");
    channelId = seedChannel();
    documentId = seedDocumentWithObject();
    String playerId = seedPlayerOfTheSimulationAndTheScenario();
    seedArticleCarryingTheDocument("article_exercise", exerciseId);
    seedArticleCarryingTheDocument("article_scenario", scenarioId);
    challengeId = seedChallengeExpectation(playerId);
    seedLessons();
    rawToken = seedUrlAccessToken(playerId);
    // Production has no ambient tenant on the non-prefixed route, where it falls back to the
    // default one. Nothing in this class sets one, but a leftover would decide the outcome.
    TenantContext.clearCurrentTenant();
    assertEquals(
        Tenant.DEFAULT_TENANT_UUID,
        TenantContext.getCurrentTenant(),
        "the ambient tenant must be the default one, as it is outside a tenant-prefixed request");
  }

  @AfterEach
  void cleanup() throws Exception {
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    fileService.deleteFile(tenantB, documentTarget);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("with the simulation's tenant in the path, the shape the platform emits")
  class WithTheSimulationTenantInThePath {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.rest.PlayerUrlTokenRoutesTenantPathTest#playerRoutes")
    @DisplayName("given a valid token, when the route is called, then the page is shown")
    void given_validToken_should_showThePage(PlayerRoute route) throws Exception {
      // Act
      MockHttpServletResponse response = asPlayer(route, "/api/tenants/" + tenantB);

      // Assert
      assertThat(response.getStatus()).as(route.name()).isEqualTo(200);
      assertThat(response.getContentAsString()).contains(fill(route.shownId()));
    }
  }

  @Nested
  @DisplayName("with another tenant in the path")
  class WithAnotherTenantInThePath {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.rest.PlayerUrlTokenRoutesTenantPathTest#playerRoutes")
    @DisplayName(
        "given a valid token, when the route is called under the default tenant, then nothing of"
            + " the simulation is read")
    void given_validTokenUnderAnotherTenant_should_readNothingOfTheSimulation(PlayerRoute route)
        throws Exception {
      // Act
      MockHttpServletResponse response =
          asPlayer(route, "/api/tenants/" + Tenant.DEFAULT_TENANT_UUID);

      // Assert
      assertRefused(route, response);
    }
  }

  @Nested
  @DisplayName("on the non-prefixed route, which names no tenant and which nothing emits")
  @WaivesUnscopedReads(
      value =
          "the route names no tenant, so its scope is empty: the channel of the player articles"
              + " route is read by id before the simulation, finds no channel and the route answers"
              + " 404, the refusal this test asserts",
      reads = {"io.openaev.service.ChannelService.validateArticles tables=channels"})
  class OnTheNonPrefixedRoute {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.rest.PlayerUrlTokenRoutesTenantPathTest#playerRoutes")
    @DisplayName(
        "given a valid token, when the route is called with no tenant named, then nothing of the"
            + " simulation is read")
    void given_validTokenWithNoTenantNamed_should_readNothingOfTheSimulation(PlayerRoute route)
        throws Exception {
      // Act
      MockHttpServletResponse response = asPlayer(route, "/api");

      // Assert
      assertRefused(route, response);
    }
  }

  private void assertRefused(PlayerRoute route, MockHttpServletResponse response) throws Exception {
    assertThat(response.getStatus()).as(route.name()).isEqualTo(route.refusal());
    assertThat(response.getContentAsString()).doesNotContain(fill(route.shownId()));
  }

  private MockHttpServletResponse asPlayer(PlayerRoute route, String prefix) throws Exception {
    String uri = prefix + fill(route.path());
    MockHttpServletRequestBuilder request =
        route.method() == HttpMethod.POST
            ? post(uri).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(route.body())
            : get(uri);
    return mvc.perform(request.cookie(new Cookie(URL_ACCESS_COOKIE_NAME, rawToken)))
        .andReturn()
        .getResponse();
  }

  private String fill(String template) {
    return template
        .replace("{exercise}", exerciseId)
        .replace("{scenario}", scenarioId)
        .replace("{channel}", channelId)
        .replace("{document}", documentId)
        .replace("{challenge}", challengeId)
        .replace("{category}", categoryId)
        .replace("{question}", questionId);
  }

  // -- SEEDING (committed, cross-thread visible) --

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "player-route-" + id);
    seededRows.add(new String[] {"tenants", "tenant_id", id});
    return id;
  }

  /** A row of tenant B holding only its id and its name. */
  private String seedRow(String table, String idColumn, String nameColumn) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO "
            + table
            + " ("
            + idColumn
            + ", "
            + nameColumn
            + ", tenant_id) VALUES (?, ?, ?)",
        id,
        "player-route-" + id,
        tenantB);
    seededRows.add(new String[] {table, idColumn, id});
    return id;
  }

  private String seedChannel() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO channels (channel_id, channel_type, channel_name, tenant_id)"
            + " VALUES (?, 'newsletter', ?, ?)",
        id,
        "player-route-" + id,
        tenantB);
    seededRows.add(new String[] {"channels", "channel_id", id});
    return id;
  }

  /** A document of tenant B whose bytes are written under B's storage prefix. */
  private String seedDocumentWithObject() throws Exception {
    byte[] content = FILE_CONTENT.getBytes(StandardCharsets.UTF_8);
    documentTarget = UUID.randomUUID() + ".txt";
    fileService.uploadFile(
        tenantB,
        documentTarget,
        new ByteArrayInputStream(content),
        content.length,
        MediaType.TEXT_PLAIN_VALUE);
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'text/plain', ?, ?)",
        id,
        "player-route-" + id,
        documentTarget,
        tenantB);
    seededRows.add(new String[] {"documents", "document_id", id});
    return id;
  }

  /** An article of the channel carrying the document, which publishes it to the players. */
  private void seedArticleCarryingTheDocument(String parentColumn, String parentId) {
    String article = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO articles (article_id, article_channel, "
            + parentColumn
            + ", article_name) VALUES (?, ?, ?, ?)",
        article,
        channelId,
        parentId,
        "player-route-" + article);
    seededRows.add(new String[] {"articles", "article_id", article});
    jdbc.update(
        "INSERT INTO articles_documents (article_id, document_id) VALUES (?, ?)",
        article,
        documentId);
    seededRows.add(new String[] {"articles_documents", "article_id", article});
  }

  private String seedPlayerOfTheSimulationAndTheScenario() {
    String user = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        user,
        "player-route-" + user + "@openaev.io");
    seededRows.add(new String[] {"users", "user_id", user});
    String team = seedRow("teams", "team_id", "team_name");
    jdbc.update(
        "INSERT INTO exercises_teams (exercise_id, team_id) VALUES (?, ?)", exerciseId, team);
    seededRows.add(new String[] {"exercises_teams", "exercise_id", exerciseId});
    jdbc.update(
        "INSERT INTO exercises_teams_users (exercise_id, team_id, user_id) VALUES (?, ?, ?)",
        exerciseId,
        team,
        user);
    seededRows.add(new String[] {"exercises_teams_users", "exercise_id", exerciseId});
    jdbc.update(
        "INSERT INTO scenarios_teams (scenario_id, team_id) VALUES (?, ?)", scenarioId, team);
    seededRows.add(new String[] {"scenarios_teams", "scenario_id", scenarioId});
    jdbc.update(
        "INSERT INTO scenarios_teams_users (scenario_id, team_id, user_id) VALUES (?, ?, ?)",
        scenarioId,
        team,
        user);
    seededRows.add(new String[] {"scenarios_teams_users", "scenario_id", scenarioId});
    // A player sees the lessons categories of the teams it is a member of.
    jdbc.update("INSERT INTO users_teams (user_id, team_id) VALUES (?, ?)", user, team);
    seededRows.add(new String[] {"users_teams", "user_id", user});
    teamOfThePlayer = team;
    return user;
  }

  private String seedChallengeExpectation(String playerId) {
    String challenge = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO challenges (challenge_id, challenge_name, tenant_id) VALUES (?, ?, ?)",
        challenge,
        "player-route-" + challenge,
        tenantB);
    seededRows.add(new String[] {"challenges", "challenge_id", challenge});
    String inject = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, tenant_id)"
            + " VALUES (?, ?, false, true, 0, ?, ?)",
        inject,
        "player-route-" + inject,
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

  private void seedLessons() {
    categoryId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO lessons_categories (lessons_category_id, lessons_category_name,"
            + " lessons_category_order, lessons_category_exercise) VALUES (?, ?, 0, ?)",
        categoryId,
        "player-route-" + categoryId,
        exerciseId);
    seededRows.add(new String[] {"lessons_categories", "lessons_category_id", categoryId});
    jdbc.update(
        "INSERT INTO lessons_categories_teams (lessons_category_id, team_id) VALUES (?, ?)",
        categoryId,
        teamOfThePlayer);
    seededRows.add(new String[] {"lessons_categories_teams", "lessons_category_id", categoryId});
    questionId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO lessons_questions (lessons_question_id, lessons_question_content,"
            + " lessons_question_order, lessons_question_category) VALUES (?, ?, 0, ?)",
        questionId,
        "player-route-" + questionId,
        categoryId);
    seededRows.add(new String[] {"lessons_questions", "lessons_question_id", questionId});
    seededRows.add(new String[] {"lessons_answers", "lessons_answer_question", questionId});
  }

  private String seedUrlAccessToken(String playerId) {
    String raw = "player-route-token-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO url_access_token (id, token_hash, url, user_id, exercise_id, expires_at)"
            + " VALUES (?, ?, ?, ?, ?, now() + interval '30 days')",
        UUID.randomUUID().toString(),
        sha256Hex(raw),
        "/" + tenantB + "/challenges/" + exerciseId,
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
