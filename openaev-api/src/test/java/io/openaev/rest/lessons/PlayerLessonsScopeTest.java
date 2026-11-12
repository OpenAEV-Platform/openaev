package io.openaev.rest.lessons;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rules enforced by the player lessons routes of {@code ExerciseLessonsApi}:
 *
 * <ul>
 *   <li>a player route resolves within the caller's membership of the simulation: a caller who
 *       plays or observes the simulation is served, any other authenticated caller is refused;
 *   <li>the answers returned are the resolved caller's own answers, whatever the {@code userId}
 *       query parameter says;
 *   <li>an answer is written on a question of the simulation and the category named in the path,
 *       otherwise the question is not found and no row is written.
 * </ul>
 */
@Transactional
@WithMockUser
@DisplayName("Player lessons routes resolve within the caller's membership of the simulation")
class PlayerLessonsScopeTest extends IntegrationTest {

  private static final String ANSWERS = "/api/player/lessons/exercise/{exerciseId}/lessons_answers";
  private static final String CATEGORIES =
      "/api/player/lessons/exercise/{exerciseId}/lessons_categories";
  private static final String QUESTIONS =
      "/api/player/lessons/exercise/{exerciseId}/lessons_questions";
  private static final String ANSWER_FOR_QUESTION =
      "/api/player/lessons/exercise/{exerciseId}/lessons_categories/{categoryId}"
          + "/lessons_questions/{questionId}/lessons_answers";
  private static final String ANSWER_BODY =
      """
      {
        "lessons_answer_score": 80,
        "lessons_answer_positive": "kept",
        "lessons_answer_negative": "changed"
      }
      """;

  @Autowired private MockMvc mvc;

  // Simulation A: two players, a category, a question, one answer per player.
  private String simulationA;
  private String teamA;
  private String categoryA;
  private String otherCategoryA;
  private String questionA;
  private String playerOne;
  private String playerTwo;
  private String answerOfPlayerOne;
  private String answerOfPlayerTwo;
  // Simulation B: its own category and question.
  private String simulationB;
  private String categoryB;
  private String questionB;

  private String caller;

  @BeforeEach
  void seedTwoSimulationsWithLessons() {
    caller = testUserHolder.get().getId();

    simulationA = seedSimulation();
    teamA = seedTeam();
    seedTeamInSimulation(simulationA, teamA);
    playerOne = seedUser();
    playerTwo = seedUser();
    seedPlayerInSimulation(simulationA, teamA, playerOne);
    seedPlayerInSimulation(simulationA, teamA, playerTwo);
    categoryA = seedCategory(simulationA);
    otherCategoryA = seedCategory(simulationA);
    questionA = seedQuestion(categoryA);
    answerOfPlayerOne = seedAnswer(questionA, playerOne);
    answerOfPlayerTwo = seedAnswer(questionA, playerTwo);

    simulationB = seedSimulation();
    categoryB = seedCategory(simulationB);
    questionB = seedQuestion(categoryB);
  }

  @Nested
  @DisplayName("a caller who is not in the simulation")
  class CallerOutsideTheSimulation {

    @Test
    @DisplayName("the answers are refused")
    void answersAreRefused() throws Exception {
      mvc.perform(get(ANSWERS, simulationA)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the categories are refused")
    void categoriesAreRefused() throws Exception {
      mvc.perform(get(CATEGORIES, simulationA)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the questions are refused")
    void questionsAreRefused() throws Exception {
      mvc.perform(get(QUESTIONS, simulationA)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a userId naming a player does not widen the answers")
    void userIdDoesNotWidenTheAnswers() throws Exception {
      mvc.perform(get(ANSWERS, simulationA).param("userId", playerOne))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a simulation that does not exist is not found")
    void unknownSimulationIsNotFound() throws Exception {
      mvc.perform(get(ANSWERS, UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an answer is refused and no row is written")
    void answerIsRefusedAndNothingIsWritten() throws Exception {
      long before = countAnswers();

      postAnswer(simulationA, categoryA, questionA).andExpect(status().isUnauthorized());

      assertEquals(before, countAnswers());
      assertEquals(0, countAnswersOf(caller));
    }
  }

  @Nested
  @DisplayName("a caller who plays the simulation")
  class CallerInTheSimulation {

    private String callerAnswer;
    private String callerTeam;

    @BeforeEach
    void joinSimulationA() {
      callerTeam = seedTeam();
      seedTeamInSimulation(simulationA, callerTeam);
      seedPlayerInSimulation(simulationA, callerTeam, caller);
      callerAnswer = seedAnswer(questionA, caller);
    }

    @Test
    @DisplayName("without userId, only the caller's own answer is returned")
    void ownAnswerOnlyWithoutUserId() throws Exception {
      mvc.perform(get(ANSWERS, simulationA).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].lessonsanswer_id").value(callerAnswer))
          .andExpect(jsonPath("$[0].lessons_answer_user").value(caller));
    }

    @Test
    @DisplayName("with the userId of another player, still only the caller's own answer")
    void ownAnswerOnlyWithAnotherUserId() throws Exception {
      mvc.perform(get(ANSWERS, simulationA).param("userId", playerOne))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].lessons_answer_user").value(caller));
    }

    @Test
    @DisplayName("the questions of the simulation are returned")
    void questionsAreReturned() throws Exception {
      mvc.perform(get(QUESTIONS, simulationA))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].lessonsquestion_id").value(questionA));
    }

    @Test
    @DisplayName("the categories of the simulation are returned")
    void categoriesAreReturned() throws Exception {
      mvc.perform(get(CATEGORIES, simulationA)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a player sees only the categories of a team they belong to")
    void playerSeesOnlyTheirTeamsCategory() throws Exception {
      // categoryA is linked to the caller's team, otherCategoryA to a team the caller is not in.
      seedUserInTeam(caller, callerTeam);
      seedCategoryTeam(categoryA, callerTeam);
      seedCategoryTeam(otherCategoryA, teamA);

      mvc.perform(get(CATEGORIES, simulationA).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].lessonscategory_id").value(categoryA));
    }

    @Test
    @DisplayName("a question of the simulation and category in the path saves the caller's answer")
    void answerOnAQuestionOfTheSimulationIsSaved() throws Exception {
      postAnswer(simulationA, categoryA, questionA)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.lessons_answer_user").value(caller))
          .andExpect(jsonPath("$.lessons_answer_question").value(questionA));

      assertEquals(1, countAnswersOf(caller));
      assertEquals(1, countAnswersOnQuestion(questionA, caller));
    }

    @Test
    @DisplayName("a question of another simulation is not found and no row is written")
    void answerOnAQuestionOfAnotherSimulationIsNotFound() throws Exception {
      long before = countAnswers();

      // categoryB is questionB's own category, so only the simulation-ownership check can refuse
      // this: the request would be accepted if the exerciseId binding were dropped.
      postAnswer(simulationA, categoryB, questionB).andExpect(status().isNotFound());

      assertEquals(before, countAnswers());
      assertEquals(0, countAnswersOnQuestion(questionB, caller));
    }

    @Test
    @DisplayName("a category of the path that is not the question's category is not found")
    void answerWithAMismatchingCategoryIsNotFound() throws Exception {
      long before = countAnswers();

      postAnswer(simulationA, otherCategoryA, questionA).andExpect(status().isNotFound());

      assertEquals(before, countAnswers());
    }
  }

  @Nested
  @DisplayName("a caller who observes the simulation through a grant")
  class ObserverOfTheSimulation {

    @Test
    @DisplayName("the questions are returned")
    void questionsAreReturned() throws Exception {
      addGrantToCurrentUser(
          io.openaev.database.model.Grant.GRANT_RESOURCE_TYPE.SIMULATION,
          io.openaev.database.model.Grant.GRANT_TYPE.OBSERVER,
          simulationA);

      mvc.perform(get(QUESTIONS, simulationA)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an observer sees every category, whatever the team links")
    void observerSeesEveryCategory() throws Exception {
      addGrantToCurrentUser(
          io.openaev.database.model.Grant.GRANT_RESOURCE_TYPE.SIMULATION,
          io.openaev.database.model.Grant.GRANT_TYPE.OBSERVER,
          simulationA);
      // Teams restrict only players; an observer (grant) sees every category of the simulation.
      seedCategoryTeam(categoryA, teamA);

      mvc.perform(get(CATEGORIES, simulationA).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
    }
  }

  // -- HTTP --

  private ResultActions postAnswer(String simulation, String category, String question)
      throws Exception {
    return mvc.perform(
        post(ANSWER_FOR_QUESTION, simulation, category, question)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(ANSWER_BODY)
            .with(csrf()));
  }

  // -- COUNTS --

  private long countAnswers() {
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM lessons_answers")
                .getSingleResult())
        .longValue();
  }

  private long countAnswersOf(String userId) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM lessons_answers WHERE lessons_answer_user = :user")
                .setParameter("user", userId)
                .getSingleResult())
        .longValue();
  }

  private long countAnswersOnQuestion(String questionId, String userId) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM lessons_answers WHERE lessons_answer_question = :question"
                        + " AND lessons_answer_user = :user")
                .setParameter("question", questionId)
                .setParameter("user", userId)
                .getSingleResult())
        .longValue();
  }

  // -- SEEDING --

  private String seedSimulation() {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO exercises (exercise_id, exercise_name, tenant_id) VALUES (:id, :name, :tenant)",
        "id",
        id,
        "name",
        "player-lessons-" + id,
        "tenant",
        Tenant.DEFAULT_TENANT_UUID);
    return id;
  }

  private String seedTeam() {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (:id, :name, :tenant)",
        "id",
        id,
        "name",
        "player-lessons-" + id,
        "tenant",
        Tenant.DEFAULT_TENANT_UUID);
    return id;
  }

  private void seedTeamInSimulation(String simulation, String team) {
    update(
        "INSERT INTO exercises_teams (exercise_id, team_id) VALUES (:exercise, :team)",
        "exercise",
        simulation,
        "team",
        team);
  }

  private void seedUserInTeam(String user, String team) {
    update(
        "INSERT INTO users_teams (team_id, user_id) VALUES (:team, :user)",
        "team",
        team,
        "user",
        user);
  }

  private void seedCategoryTeam(String category, String team) {
    update(
        "INSERT INTO lessons_categories_teams (lessons_category_id, team_id)"
            + " VALUES (:category, :team)",
        "category",
        category,
        "team",
        team);
  }

  private String seedUser() {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status)"
            + " VALUES (:id, :email, false, 0)",
        "id",
        id,
        "email",
        "player-lessons-" + id + "@openaev.io");
    return id;
  }

  private void seedPlayerInSimulation(String simulation, String team, String user) {
    update(
        "INSERT INTO exercises_teams_users (exercise_id, team_id, user_id)"
            + " VALUES (:exercise, :team, :user)",
        "exercise",
        simulation,
        "team",
        team,
        "user",
        user);
  }

  private String seedCategory(String simulation) {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO lessons_categories (lessons_category_id, lessons_category_name,"
            + " lessons_category_order, lessons_category_exercise)"
            + " VALUES (:id, :name, 0, :exercise)",
        "id",
        id,
        "name",
        "player-lessons-" + id,
        "exercise",
        simulation);
    return id;
  }

  private String seedQuestion(String category) {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO lessons_questions (lessons_question_id, lessons_question_content,"
            + " lessons_question_order, lessons_question_category)"
            + " VALUES (:id, :content, 0, :category)",
        "id",
        id,
        "content",
        "player-lessons-" + id,
        "category",
        category);
    return id;
  }

  private String seedAnswer(String question, String user) {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO lessons_answers (lessons_answer_id, lessons_answer_positive,"
            + " lessons_answer_negative, lessons_answer_score, lessons_answer_question,"
            + " lessons_answer_user) VALUES (:id, 'kept', 'changed', 10, :question, :user)",
        "id",
        id,
        "question",
        question,
        "user",
        user);
    return id;
  }

  private void update(String sql, String... namesAndValues) {
    var query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < namesAndValues.length; i += 2) {
      query.setParameter(namesAndValues[i], namesAndValues[i + 1]);
    }
    query.executeUpdate();
    entityManager.flush();
    entityManager.clear();
  }
}
