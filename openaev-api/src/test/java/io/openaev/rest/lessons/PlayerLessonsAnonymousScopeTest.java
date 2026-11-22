package io.openaev.rest.lessons;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The anonymous player lessons path: a caller carrying a valid url access token (and no session)
 * skips the membership check and relies on {@code @UrlAccessControl}, which binds the token to its
 * simulation. This pins that an anonymous caller reads only the token user's own answers, that a
 * token minted for another simulation is refused, and which tenant the simulation is read in.
 *
 * <p>The simulation lives in a tenant of its own, not the default one, so each route shape answers
 * the same way whether or not {@code exercises} is in the active tables:
 *
 * <ul>
 *   <li>the simulation's tenant in the path is the shape the platform emits: the lessons link built
 *       by {@code ExecutionContextService} carries it as the first path segment, and the SPA
 *       prefixes every API call with it ({@code buildTenantApiPath}).
 *   <li>another tenant in the path reads the simulation in that tenant, where it does not exist.
 *   <li>the non-prefixed route names no tenant: {@code TxCtxArgumentResolver#anonymousScope}
 *       resolves {@code TxCtx.missing()}, so the simulation is not found. Nothing the platform
 *       emits takes this route.
 * </ul>
 *
 * <p>Not {@code @Transactional}: the rows are committed through an auto-committing {@link
 * JdbcTemplate} so the anonymous request thread reads them, and removed on teardown.
 */
@DisplayName("Anonymous player lessons routes resolve through the url access token")
class PlayerLessonsAnonymousScopeTest extends IntegrationTest {

  private static final String ANSWERS_PATH =
      "/player/lessons/exercise/{exerciseId}/lessons_answers";
  private static final String TENANT_ANSWERS = TENANT_PREFIX + ANSWERS_PATH;
  private static final String LEGACY_ANSWERS = "/api" + ANSWERS_PATH;

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String[]> seededRows = new ArrayList<>();

  private String tenantOfA;
  private String simulationA;
  private String tokenUser;
  private String answerOfTokenUser;
  private String rawTokenForA;
  private String rawTokenForB;

  @BeforeEach
  void seedTwoSimulationsWithTokens() {
    jdbc = new JdbcTemplate(dataSource);

    tenantOfA = seedTenant();
    simulationA = seedSimulation(tenantOfA);
    String team = seedTeam(tenantOfA);
    seedTeamInSimulation(simulationA, team);
    tokenUser = seedUser();
    seedPlayerInSimulation(simulationA, team, tokenUser);
    String otherUser = seedUser();
    seedPlayerInSimulation(simulationA, team, otherUser);
    String question = seedQuestion(seedCategory(simulationA));
    answerOfTokenUser = seedAnswer(question, tokenUser);
    seedAnswer(question, otherUser);
    rawTokenForA = seedUrlAccessToken(simulationA, tokenUser);

    String simulationB = seedSimulation(tenantOfA);
    rawTokenForB = seedUrlAccessToken(simulationB, seedUser());

    // Production has no ambient tenant on the non-prefixed route, where it falls back to the
    // default one. Nothing in this class sets one, but a leftover would decide the outcome.
    TenantContext.clearCurrentTenant();
    assertEquals(
        Tenant.DEFAULT_TENANT_UUID,
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
  @DisplayName("with the simulation's tenant in the path, the shape the platform emits")
  class WithTheSimulationTenantInThePath {

    @Test
    @DisplayName("given a valid token, when the answers are listed, then only its user's are")
    void given_validToken_should_returnOnlyTheTokenUsersOwnAnswers() throws Exception {
      // Act
      ResultActions response = withToken(get(TENANT_ANSWERS, tenantOfA, simulationA), rawTokenForA);

      // Assert
      response
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].lessonsanswer_id").value(answerOfTokenUser))
          .andExpect(jsonPath("$[0].lessons_answer_user").value(tokenUser));
    }

    @Test
    @DisplayName(
        "given a token minted for another simulation, when the answers are listed, then it is refused")
    void given_tokenForAnotherSimulation_should_beRefused() throws Exception {
      // Act
      ResultActions response = withToken(get(TENANT_ANSWERS, tenantOfA, simulationA), rawTokenForB);

      // Assert
      response.andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("with another tenant in the path")
  class WithAnotherTenantInThePath {

    @Test
    @DisplayName(
        "given a valid token, when the answers are listed under the default tenant, then the"
            + " simulation is not found")
    void given_validTokenUnderAnotherTenant_should_notFindTheSimulation() throws Exception {
      // Act
      ResultActions response =
          withToken(get(TENANT_ANSWERS, Tenant.DEFAULT_TENANT_UUID, simulationA), rawTokenForA);

      // Assert
      response.andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("on the non-prefixed route, which names no tenant and which nothing emits")
  class OnTheNonPrefixedRoute {

    @Test
    @DisplayName(
        "given a valid token, when the answers are listed with no tenant named, then the"
            + " simulation is not found")
    void given_validTokenWithNoTenantNamed_should_notFindTheSimulation() throws Exception {
      // Act
      ResultActions response = withToken(get(LEGACY_ANSWERS, simulationA), rawTokenForA);

      // Assert
      response.andExpect(status().isNotFound());
    }
  }

  private ResultActions withToken(MockHttpServletRequestBuilder request, String rawToken)
      throws Exception {
    return mvc.perform(
        request
            .accept(MediaType.APPLICATION_JSON)
            .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, rawToken)));
  }

  // -- SEEDING (committed, cross-thread visible) --

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "player-lessons-anon-" + id);
    seededRows.add(new String[] {"tenants", "tenant_id", id});
    return id;
  }

  private String seedSimulation(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "player-lessons-anon-" + id,
        tenantId);
    seededRows.add(new String[] {"exercises", "exercise_id", id});
    return id;
  }

  private String seedTeam(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "player-lessons-anon-" + id,
        tenantId);
    seededRows.add(new String[] {"teams", "team_id", id});
    return id;
  }

  private void seedTeamInSimulation(String simulation, String team) {
    jdbc.update(
        "INSERT INTO exercises_teams (exercise_id, team_id) VALUES (?, ?)", simulation, team);
    seededRows.add(new String[] {"exercises_teams", "exercise_id", simulation});
  }

  private String seedUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        id,
        "player-lessons-anon-" + id + "@openaev.io");
    seededRows.add(new String[] {"users", "user_id", id});
    return id;
  }

  private void seedPlayerInSimulation(String simulation, String team, String user) {
    jdbc.update(
        "INSERT INTO exercises_teams_users (exercise_id, team_id, user_id) VALUES (?, ?, ?)",
        simulation,
        team,
        user);
    seededRows.add(new String[] {"exercises_teams_users", "user_id", user});
  }

  private String seedCategory(String simulation) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO lessons_categories (lessons_category_id, lessons_category_name,"
            + " lessons_category_order, lessons_category_exercise) VALUES (?, ?, 0, ?)",
        id,
        "player-lessons-anon-" + id,
        simulation);
    seededRows.add(new String[] {"lessons_categories", "lessons_category_id", id});
    return id;
  }

  private String seedQuestion(String category) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO lessons_questions (lessons_question_id, lessons_question_content,"
            + " lessons_question_order, lessons_question_category) VALUES (?, ?, 0, ?)",
        id,
        "player-lessons-anon-" + id,
        category);
    seededRows.add(new String[] {"lessons_questions", "lessons_question_id", id});
    return id;
  }

  private String seedAnswer(String question, String user) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO lessons_answers (lessons_answer_id, lessons_answer_positive,"
            + " lessons_answer_negative, lessons_answer_score, lessons_answer_question,"
            + " lessons_answer_user) VALUES (?, 'kept', 'changed', 10, ?, ?)",
        id,
        question,
        user);
    seededRows.add(new String[] {"lessons_answers", "lessons_answer_id", id});
    return id;
  }

  private String seedUrlAccessToken(String simulation, String user) {
    String raw = "player-lessons-anon-token-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO url_access_token (id, token_hash, url, user_id, exercise_id, expires_at)"
            + " VALUES (?, ?, ?, ?, ?, now() + interval '30 days')",
        UUID.randomUUID().toString(),
        sha256Hex(raw),
        "/" + tenantOfA + "/lessons/simulation/" + simulation,
        user,
        simulation);
    seededRows.add(new String[] {"url_access_token", "user_id", user});
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
