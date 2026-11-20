package io.openaev.rest.lessons;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
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
 * simulation. This pins that an anonymous caller reads only the token user's own answers, and that
 * a token minted for another simulation is refused.
 *
 * <p>Not {@code @Transactional}: the rows are committed through an auto-committing {@link
 * JdbcTemplate} so the anonymous request thread reads them, and removed on teardown.
 */
@DisplayName("Anonymous player lessons routes resolve through the url access token")
class PlayerLessonsAnonymousScopeTest extends IntegrationTest {

  private static final String ANSWERS = "/api/player/lessons/exercise/{exerciseId}/lessons_answers";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String[]> seededRows = new ArrayList<>();

  private String simulationA;
  private String tokenUser;
  private String answerOfTokenUser;
  private String rawTokenForA;
  private String rawTokenForB;

  @BeforeEach
  void seedTwoSimulationsWithTokens() {
    jdbc = new JdbcTemplate(dataSource);

    simulationA = seedSimulation();
    String team = seedTeam();
    seedTeamInSimulation(simulationA, team);
    tokenUser = seedUser();
    seedPlayerInSimulation(simulationA, team, tokenUser);
    String otherUser = seedUser();
    seedPlayerInSimulation(simulationA, team, otherUser);
    String question = seedQuestion(seedCategory(simulationA));
    answerOfTokenUser = seedAnswer(question, tokenUser);
    seedAnswer(question, otherUser);
    rawTokenForA = seedUrlAccessToken(simulationA, tokenUser);

    String simulationB = seedSimulation();
    rawTokenForB = seedUrlAccessToken(simulationB, seedUser());
  }

  @AfterEach
  void cleanup() {
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
  }

  @Test
  @DisplayName("a valid token returns only the token user's own answers")
  void validTokenReturnsTheTokenUsersOwnAnswers() throws Exception {
    withToken(get(ANSWERS, simulationA).accept(MediaType.APPLICATION_JSON), rawTokenForA)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].lessonsanswer_id").value(answerOfTokenUser))
        .andExpect(jsonPath("$[0].lessons_answer_user").value(tokenUser));
  }

  @Test
  @DisplayName("a token minted for another simulation is refused")
  void tokenForAnotherSimulationIsRefused() throws Exception {
    withToken(get(ANSWERS, simulationA).accept(MediaType.APPLICATION_JSON), rawTokenForB)
        .andExpect(status().isUnauthorized());
  }

  private ResultActions withToken(MockHttpServletRequestBuilder request, String rawToken)
      throws Exception {
    return mvc.perform(request.cookie(new Cookie(URL_ACCESS_COOKIE_NAME, rawToken)));
  }

  // -- SEEDING (committed, cross-thread visible) --

  private String seedSimulation() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "player-lessons-anon-" + id,
        Tenant.DEFAULT_TENANT_UUID);
    seededRows.add(new String[] {"exercises", "exercise_id", id});
    return id;
  }

  private String seedTeam() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "player-lessons-anon-" + id,
        Tenant.DEFAULT_TENANT_UUID);
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
        "/api/player/lessons/exercise/" + simulation + "/lessons_answers",
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
