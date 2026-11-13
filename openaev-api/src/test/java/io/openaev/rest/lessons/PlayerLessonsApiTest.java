package io.openaev.rest.lessons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.repository.LessonsAnswerRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.LessonsCategoryFixture;
import io.openaev.utils.fixtures.LessonsQuestionFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.LessonsCategoryComposer;
import io.openaev.utils.fixtures.composers.LessonsQuestionsComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("Player lessons API")
class PlayerLessonsApiTest extends IntegrationTest {

  private static final String PLAYER_LESSONS_URI = "/api/player/lessons/exercise/";
  private static final String ANSWER_BODY =
      "{\"lessons_answer_score\":5,\"lessons_answer_positive\":\"p\","
          + "\"lessons_answer_negative\":\"n\"}";

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private LessonsCategoryComposer lessonsCategoryComposer;
  @Autowired private LessonsQuestionsComposer lessonsQuestionsComposer;
  @Autowired private LessonsAnswerRepository lessonsAnswerRepository;

  private String playedSimulationId;
  private String playedCategoryId;
  private String playedQuestionId;
  private String otherSimulationId;
  private String otherCategoryId;
  private String otherQuestionId;

  @BeforeEach
  void setUp() {
    exerciseComposer.reset();
    teamComposer.reset();
    lessonsCategoryComposer.reset();
    lessonsQuestionsComposer.reset();

    LessonsCategoryComposer.Composer playedCategory = categoryWithQuestion();
    Exercise played =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withTeam(teamComposer.forTeam(TeamFixture.getTeam(testUserHolder.get())))
            .withTeamUsers()
            .withLessonCategory(playedCategory)
            .persist()
            .get();
    LessonsCategoryComposer.Composer otherCategory = categoryWithQuestion();
    Exercise other =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withLessonCategory(otherCategory)
            .persist()
            .get();
    entityManager.flush();
    entityManager.clear();

    playedSimulationId = played.getId();
    playedCategoryId = playedCategory.get().getId();
    playedQuestionId = playedCategory.get().getQuestions().getFirst().getId();
    otherSimulationId = other.getId();
    otherCategoryId = otherCategory.get().getId();
    otherQuestionId = otherCategory.get().getQuestions().getFirst().getId();
  }

  private LessonsCategoryComposer.Composer categoryWithQuestion() {
    return lessonsCategoryComposer
        .forLessonsCategory(LessonsCategoryFixture.createDefaultLessonsCategory())
        .withLessonsQuestion(
            lessonsQuestionsComposer.forLessonsQuestion(
                LessonsQuestionFixture.createDefaultLessonsQuestion()));
  }

  private static MockHttpServletRequestBuilder answer(
      String simulationId, String categoryId, String questionId) {
    return post(PLAYER_LESSONS_URI
            + simulationId
            + "/lessons_categories/"
            + categoryId
            + "/lessons_questions/"
            + questionId
            + "/lessons_answers")
        .contentType(MediaType.APPLICATION_JSON)
        .content(ANSWER_BODY);
  }

  private boolean hasAnswered(String questionId) {
    entityManager.flush();
    entityManager.clear();
    return lessonsAnswerRepository
        .findByUserIdAndQuestionId(testUserHolder.get().getId(), questionId)
        .isPresent();
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Simulation or question the user does not play")
  class NotPlayed {

    Stream<Arguments> callsOutsideThePlayedSimulation() {
      return Stream.of(
          call(
              "list the categories of a simulation not played",
              () -> get(PLAYER_LESSONS_URI + otherSimulationId + "/lessons_categories")),
          call(
              "list the questions of a simulation not played",
              () -> get(PLAYER_LESSONS_URI + otherSimulationId + "/lessons_questions")),
          call(
              "list the answers of a simulation not played",
              () -> get(PLAYER_LESSONS_URI + otherSimulationId + "/lessons_answers")),
          call(
              "answer a question of a simulation not played",
              () -> answer(otherSimulationId, otherCategoryId, otherQuestionId)),
          call(
              "answer a question of another simulation through the played one",
              () -> answer(playedSimulationId, playedCategoryId, otherQuestionId)),
          call(
              "answer a question of another category of the played simulation path",
              () -> answer(playedSimulationId, otherCategoryId, otherQuestionId)));
    }

    private static Arguments call(String name, Supplier<MockHttpServletRequestBuilder> request) {
      return Arguments.of(name, request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callsOutsideThePlayedSimulation")
    @DisplayName("Given a simulation or question not played, should answer not found")
    void given_notPlayed_should_answerNotFoundAndRecordNothing(
        String name, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
      // Act
      mvc.perform(request.get().with(csrf()))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(hasAnswered(otherQuestionId)).isFalse();
    }
  }

  @Nested
  @DisplayName("Simulation the user plays")
  class Played {

    @Test
    @DisplayName("Given a played simulation, should list its categories")
    void given_playedSimulation_should_listItsCategories() throws Exception {
      // Act
      mvc.perform(get(PLAYER_LESSONS_URI + playedSimulationId + "/lessons_categories"))
          // Assert
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "Given a member of a team the lessons target, not enabled in the simulation, should record"
            + " the answer")
    void given_memberOfALessonsTeamNotEnabled_should_recordTheAnswer() throws Exception {
      // Arrange
      LessonsCategoryComposer.Composer category = categoryWithQuestion();
      category
          .get()
          .setTeams(
              List.of(
                  teamComposer.forTeam(TeamFixture.getTeam(testUserHolder.get())).persist().get()));
      Exercise simulation =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withLessonCategory(category)
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();
      LessonsCategory lessonsCategory = category.get();
      String questionId = lessonsCategory.getQuestions().getFirst().getId();

      // Act
      mvc.perform(answer(simulation.getId(), lessonsCategory.getId(), questionId).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      assertThat(hasAnswered(questionId)).isTrue();
    }

    @Test
    @DisplayName("Given a question of the played simulation, should record the answer")
    void given_questionOfPlayedSimulation_should_recordTheAnswer() throws Exception {
      // Act
      mvc.perform(answer(playedSimulationId, playedCategoryId, playedQuestionId).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      assertThat(hasAnswered(playedQuestionId)).isTrue();
    }
  }
}
