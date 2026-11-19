package io.openaev.rest.lessons;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.LessonsQuestion;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.LessonsQuestionRepository;
import io.openaev.utils.fixtures.LessonsCategoryFixture;
import io.openaev.utils.fixtures.LessonsQuestionFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.composers.LessonsCategoryComposer;
import io.openaev.utils.fixtures.composers.LessonsQuestionsComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
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
@WithMockUser(isAdmin = true)
@DisplayName("Scenario lessons API")
class ScenarioLessonsApiTest extends IntegrationTest {

  private static final String CATEGORY_BODY =
      "{\"lessons_category_name\":\"renamed\",\"lessons_category_description\":\"d\","
          + "\"lessons_category_order\":1}";
  private static final String TEAMS_BODY = "{\"lessons_category_teams\":[]}";
  private static final String QUESTION_BODY =
      "{\"lessons_question_content\":\"changed\",\"lessons_question_explanation\":\"e\","
          + "\"lessons_question_order\":1}";

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private LessonsCategoryComposer lessonsCategoryComposer;
  @Autowired private LessonsQuestionsComposer lessonsQuestionsComposer;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @Autowired private LessonsQuestionRepository lessonsQuestionRepository;

  private String scenarioId;
  private String categoryId;
  private String questionId;
  private String otherCategoryId;
  private String otherQuestionId;

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    teamComposer.reset();
    lessonsCategoryComposer.reset();
    lessonsQuestionsComposer.reset();

    Scenario scenario = scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
    Scenario otherScenario =
        scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
    LessonsCategory category = persistCategoryWithQuestion(scenario);
    LessonsCategory otherCategory = persistCategoryWithQuestion(otherScenario);
    otherCategory.setTeams(
        List.of(teamComposer.forTeam(TeamFixture.getDefaultTeam()).persist().get()));
    entityManager.flush();
    entityManager.clear();

    scenarioId = scenario.getId();
    categoryId = category.getId();
    questionId = category.getQuestions().getFirst().getId();
    otherCategoryId = otherCategory.getId();
    otherQuestionId = otherCategory.getQuestions().getFirst().getId();
  }

  private LessonsCategory persistCategoryWithQuestion(Scenario scenario) {
    LessonsCategory category = LessonsCategoryFixture.createDefaultLessonsCategory();
    category.setScenario(scenario);
    return lessonsCategoryComposer
        .forLessonsCategory(category)
        .withLessonsQuestion(
            lessonsQuestionsComposer.forLessonsQuestion(
                LessonsQuestionFixture.createDefaultLessonsQuestion()))
        .persist()
        .get();
  }

  private String categoryUri(String lessonsCategoryId) {
    return SCENARIO_URI + "/" + scenarioId + "/lessons_categories/" + lessonsCategoryId;
  }

  private String questionUri(String lessonsCategoryId, String lessonsQuestionId) {
    return categoryUri(lessonsCategoryId) + "/lessons_questions/" + lessonsQuestionId;
  }

  private static MockHttpServletRequestBuilder withBody(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  /** The rows of the other scenario the calls could reach: name, teams, questions. */
  private String otherScenarioRows() {
    entityManager.flush();
    entityManager.clear();
    LessonsCategory category = lessonsCategoryRepository.findById(otherCategoryId).orElseThrow();
    LessonsQuestion question = lessonsQuestionRepository.findById(otherQuestionId).orElseThrow();
    return category.getName()
        + "|"
        + category.getTeams().size()
        + "|"
        + category.getQuestions().size()
        + "|"
        + question.getContent();
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Child of another scenario")
  class ChildOfAnotherScenario {

    Stream<Arguments> callsOnAChildOfAnotherScenario() {
      return Stream.of(
          call(
              "update its category",
              () -> withBody(put(categoryUri(otherCategoryId)), CATEGORY_BODY)),
          call("delete its category", () -> delete(categoryUri(otherCategoryId))),
          call(
              "set the teams of its category",
              () -> withBody(put(categoryUri(otherCategoryId) + "/teams"), TEAMS_BODY)),
          call(
              "list the questions of its category",
              () -> get(categoryUri(otherCategoryId) + "/lessons_questions")),
          call(
              "add a question to its category",
              () ->
                  withBody(
                      post(categoryUri(otherCategoryId) + "/lessons_questions"), QUESTION_BODY)),
          call(
              "update its question",
              () -> withBody(put(questionUri(categoryId, otherQuestionId)), QUESTION_BODY)),
          call("delete its question", () -> delete(questionUri(categoryId, otherQuestionId))),
          call(
              "update its question through its own category",
              () -> withBody(put(questionUri(otherCategoryId, otherQuestionId)), QUESTION_BODY)),
          call(
              "delete its question through its own category",
              () -> delete(questionUri(otherCategoryId, otherQuestionId))));
    }

    private static Arguments call(String name, Supplier<MockHttpServletRequestBuilder> request) {
      return Arguments.of(name, request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callsOnAChildOfAnotherScenario")
    @DisplayName("Given a child of another scenario, should answer not found and change nothing")
    void given_childOfAnotherScenario_should_answerNotFoundAndChangeNothing(
        String name, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
      // Arrange
      String before = otherScenarioRows();

      // Act
      mvc.perform(request.get().with(csrf()))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(otherScenarioRows()).isEqualTo(before);
    }
  }

  @Nested
  @DisplayName("Child of the scenario")
  class ChildOfTheScenario {

    @Test
    @DisplayName("Given its own category, should update it")
    void given_ownCategory_should_updateIt() throws Exception {
      // Act
      mvc.perform(withBody(put(categoryUri(categoryId)), CATEGORY_BODY).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(lessonsCategoryRepository.findById(categoryId).orElseThrow().getName())
          .isEqualTo("renamed");
    }

    @Test
    @DisplayName("Given its own question, should update it")
    void given_ownQuestion_should_updateIt() throws Exception {
      // Act
      mvc.perform(withBody(put(questionUri(categoryId, questionId)), QUESTION_BODY).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(lessonsQuestionRepository.findById(questionId).orElseThrow().getContent())
          .isEqualTo("changed");
    }

    @Test
    @DisplayName("Given its own question, should delete it")
    void given_ownQuestion_should_deleteIt() throws Exception {
      // Act
      mvc.perform(delete(questionUri(categoryId, questionId)).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(lessonsQuestionRepository.findById(questionId)).isEmpty();
    }

    @Test
    @DisplayName("Given its own category, should delete it")
    void given_ownCategory_should_deleteIt() throws Exception {
      // Act
      mvc.perform(delete(categoryUri(categoryId)).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(lessonsCategoryRepository.findById(categoryId)).isEmpty();
    }
  }
}
