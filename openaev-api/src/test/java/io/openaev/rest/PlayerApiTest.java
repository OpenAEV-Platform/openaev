package io.openaev.rest;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.PlayerFixture.PLAYER_FIXTURE_FIRSTNAME;
import static io.openaev.utils.fixtures.UrlAccessTokenFixture.DEFAULT_RAW_TOKEN;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.text.MessageFormat;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class PlayerApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Value("${openbas.admin.email:${openaev.admin.email:#{null}}}")
  private String adminEmail;

  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private UserRepository userRepository;

  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private LessonsCategoryComposer lessonsCategoryComposer;
  @Autowired private LessonsQuestionsComposer lessonsQuestionsComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private UrlAccessTokenComposer urlAccessTokenComposer;
  @Autowired private LessonsAnswersComposer lessonsAnswersComposer;

  @Autowired private EntityManager entityManager;

  @Nested
  @DisplayName("Player Lessons API")
  class PlayerLessonsApi {
    @BeforeEach
    void before() {
      exerciseComposer.reset();
      lessonsCategoryComposer.reset();
      lessonsQuestionsComposer.reset();
      userComposer.reset();
      urlAccessTokenComposer.reset();
      lessonsAnswersComposer.reset();
    }

    private record PlayerApiObjectWrappers(
        ExerciseComposer.Composer simulationWrapper,
        UserComposer.Composer userWrapper,
        UserComposer.Composer otherUserWrapper,
        LessonsCategoryComposer.Composer categoryWrapper,
        LessonsQuestionsComposer.Composer questionWrapper) {}

    private PlayerApiObjectWrappers getExerciseWrapper() {
      UserComposer.Composer userWrapper =
          userComposer.forUser(UserFixture.getUserWithDefaultEmail());
      UserComposer.Composer otherUserWrapper =
          userComposer.forUser(UserFixture.getUserWithDefaultEmail());
      LessonsCategoryComposer.Composer categoryWrapper =
          lessonsCategoryComposer.forLessonsCategory(
              LessonsCategoryFixture.createDefaultLessonsCategory());
      LessonsQuestionsComposer.Composer questionWrapper =
          lessonsQuestionsComposer.forLessonsQuestion(
              LessonsQuestionFixture.createDefaultLessonsQuestion());
      ExerciseComposer.Composer simulationWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withLessonCategory(
                  categoryWrapper.withLessonsQuestion(
                      questionWrapper
                          .withAnswer(
                              lessonsAnswersComposer
                                  .forLessonsAnswer(LessonsAnswerFixture.createLessonsAnswer())
                                  .withUser(userWrapper))
                          .withAnswer(
                              lessonsAnswersComposer
                                  .forLessonsAnswer(LessonsAnswerFixture.createLessonsAnswer())
                                  .withUser(otherUserWrapper))))
              .persist();
      return new PlayerApiObjectWrappers(
          simulationWrapper, userWrapper, otherUserWrapper, categoryWrapper, questionWrapper);
    }

    @Nested
    @DisplayName("Lessons Questions API")
    class LessonsQuestionsApi {
      private final String urlMask = "/api/player/lessons/exercise/{0}/lessons_questions";

      private String getUrl(String simulationId) {
        return MessageFormat.format(urlMask, simulationId);
      }

      private String getUrl(String simulationId, String userId) {
        return MessageFormat.format(
            "{0}?userId={1}", MessageFormat.format(urlMask, simulationId), userId);
      }

      @Nested
      @DisplayName("With no URL access control cookie")
      class WithNoUrlAccessControlCookie {
        @Test
        @DisplayName("Given no specific userId on the query string, throw Unauthorised")
        public void given_noSpecificUserIdOnTheQueryString_throw401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(getUrl(wrappers.simulationWrapper().get().getId()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given specific userId on the query string, throw Unauthorised")
        public void given_specificUserIdOnTheQueryString_throw401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(getUrl(
                          wrappers.simulationWrapper().get().getId(),
                          wrappers.userWrapper().get().getId()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }
      }

      @Nested
      @DisplayName("With invalid URL access control cookie")
      class WithInvalidUrlAccessControlCookie {
        @Test
        @DisplayName("Given no specific userId on the query string, throw Unauthorised")
        public void given_noSpecificUserIdOnTheQueryString_throw401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(getUrl(wrappers.simulationWrapper().get().getId()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, "bad cookie"))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given specific userId on the query string, throw Unauthorised")
        public void given_specificUserIdOnTheQueryString_throw401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(getUrl(
                          wrappers.simulationWrapper().get().getId(),
                          wrappers.userWrapper().get().getId()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, "bad cookie"))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }
      }

      @Nested
      @DisplayName("With valid URL access control cookie")
      class WithValidUrlAccessControlCookie {

        private UrlAccessTokenComposer.Composer createUrlAccessToken(
            Exercise simulation, User user, String url) {
          return urlAccessTokenComposer.forToken(
              UrlAccessTokenFixture.createValidToken(simulation, user, url));
        }

        @Test
        @DisplayName("Given no specific userId on the query string, 200 OK")
        public void given_noSpecificUserIdOnTheQueryString_200OK() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          String url = getUrl(wrappers.simulationWrapper().get().getId());
          createUrlAccessToken(
                  wrappers.simulationWrapper().get(), wrappers.userWrapper().get(), url)
              .persist();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(url)
                      .contentType(MediaType.APPLICATION_JSON)
                      .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Given specific userId on the query string, 200 OK")
        public void given_specificUserIdOnTheQueryString_200OK() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          String url =
              getUrl(
                  wrappers.simulationWrapper().get().getId(), wrappers.userWrapper().get().getId());
          createUrlAccessToken(
                  wrappers.simulationWrapper().get(), wrappers.userWrapper().get(), url)
              .persist();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(url)
                      .contentType(MediaType.APPLICATION_JSON)
                      .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());
        }
      }
    }

    @Nested
    @DisplayName("Lessons Answers API")
    class LessonsAnswersApi {
      private final String getAnswersUrlMask = "/api/player/lessons/exercise/{0}/lessons_answers";

      private String getAnswersUrl(String simulationId) {
        return MessageFormat.format(getAnswersUrlMask, simulationId);
      }

      private String getAnswersUrl(String simulationId, String userId) {
        return MessageFormat.format(
            "{0}?userId={1}", MessageFormat.format(getAnswersUrlMask, simulationId), userId);
      }

      private final String postAnswersUrlMask =
          "/api/player/lessons/exercise/{0}/lessons_categories/{1}/lessons_questions/{2}/lessons_answers";

      private String postAnswersUrl(String simulationId, String categoryId, String questionId) {
        return MessageFormat.format(postAnswersUrlMask, simulationId, categoryId, questionId);
      }

      private String postAnswersUrl(
          String simulationId, String categoryId, String questionId, String userId) {
        return MessageFormat.format(
            "{0}?userId={1}",
            MessageFormat.format(postAnswersUrlMask, simulationId, categoryId, questionId), userId);
      }

      @Nested
      @DisplayName("With no URL access control cookie")
      class WithNoUrlAccessControlCookie {
        @Test
        @DisplayName("Given no specific userId on the query string, get answers throw Unauthorised")
        public void given_noSpecificUserIdOnTheQueryString_throw401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(getAnswersUrl(wrappers.simulationWrapper().get().getId()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given specific userId on the query string, get answers throw Unauthorised")
        public void given_specificUserIdOnTheQueryString_throw401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(getAnswersUrl(
                          wrappers.simulationWrapper().get().getId(),
                          wrappers.userWrapper().get().getId()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName(
            "Given no specific userId on the query string, post answers throw Unauthorised")
        public void given_noSpecificUserIdOnTheQueryString_postAnswersThrow401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  post(postAnswersUrl(
                          wrappers.simulationWrapper().get().getId(),
                          wrappers.categoryWrapper().get().getId(),
                          wrappers.questionWrapper().get().getId()))
                      .content(
                          """
                          {
                            "lessons_answer_score":80,
                            "lessons_answer_positive":"test",
                            "lessons_answer_negative":"test"
                          }
                          """)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given specific userId on the query string, post answers throw Unauthorised")
        public void given_specificUserIdOnTheQueryString_postAnswersThrow401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  post(postAnswersUrl(
                          wrappers.simulationWrapper().get().getId(),
                          wrappers.categoryWrapper().get().getId(),
                          wrappers.questionWrapper().get().getId(),
                          wrappers.userWrapper().get().getId()))
                      .content(
                          """
                          {
                            "lessons_answer_score":80,
                            "lessons_answer_positive":"test",
                            "lessons_answer_negative":"test"
                          }
                          """)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }
      }

      @Nested
      @DisplayName("With invalid URL access control cookie")
      class WithInvalidUrlAccessControlCookie {
        @Test
        @DisplayName("Given no specific userId on the query string, throw Unauthorised")
        public void given_noSpecificUserIdOnTheQueryString_throw401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(getAnswersUrl(wrappers.simulationWrapper().get().getId()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, "bad cookie"))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given specific userId on the query string, throw Unauthorised")
        public void given_specificUserIdOnTheQueryString_throw401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  get(getAnswersUrl(
                          wrappers.simulationWrapper().get().getId(),
                          wrappers.userWrapper().get().getId()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, "bad cookie"))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName(
            "Given no specific userId on the query string, post answers throw Unauthorised")
        public void given_noSpecificUserIdOnTheQueryString_postAnswersThrow401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  post(postAnswersUrl(
                          wrappers.simulationWrapper().get().getId(),
                          wrappers.categoryWrapper().get().getId(),
                          wrappers.questionWrapper().get().getId()))
                      .content(
                          """
                          {
                            "lessons_answer_score":80,
                            "lessons_answer_positive":"test",
                            "lessons_answer_negative":"test"
                          }
                          """)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given specific userId on the query string, post answers throw Unauthorised")
        public void given_specificUserIdOnTheQueryString_postAnswersThrow401() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          entityManager.flush();
          entityManager.clear();

          mvc.perform(
                  post(postAnswersUrl(
                          wrappers.simulationWrapper().get().getId(),
                          wrappers.categoryWrapper().get().getId(),
                          wrappers.questionWrapper().get().getId(),
                          wrappers.userWrapper().get().getId()))
                      .content(
                          """
                          {
                            "lessons_answer_score":80,
                            "lessons_answer_positive":"test",
                            "lessons_answer_negative":"test"
                          }
                          """)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isUnauthorized());
        }
      }

      @Nested
      @DisplayName("With valid URL access control cookie")
      class WithValidUrlAccessControlCookie {

        private UrlAccessTokenComposer.Composer createUrlAccessToken(
            Exercise simulation, User user, String url) {
          return urlAccessTokenComposer.forToken(
              UrlAccessTokenFixture.createValidToken(simulation, user, url));
        }

        @Test
        @DisplayName(
            "Given valid User A cookie and no id query string, then answer attributed to User A")
        public void
            given_validUserACookieAndNoSpecificUserIdOnTheQueryString_answerAttributedUserA()
                throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          String url =
              postAnswersUrl(
                  wrappers.simulationWrapper().get().getId(),
                  wrappers.categoryWrapper().get().getId(),
                  wrappers.questionWrapper().get().getId());
          createUrlAccessToken(
                  wrappers.simulationWrapper().get(), wrappers.userWrapper().get(), url)
              .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      post(url)
                          .content(
                              """
                              {
                                "lessons_answer_score":80,
                                "lessons_answer_positive":"test",
                                "lessons_answer_negative":"test"
                              }
                              """)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString)
              .node("lessons_answer_user")
              .isEqualTo(wrappers.userWrapper().get().getId());
        }

        @Test
        @DisplayName(
            "Given valid User A cookie and specified User B id query string, then answer attributed to User A")
        public void given_validUserACookieAndSpecificUserIdOnTheQueryString_answerAttributedUserA()
            throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          String url =
              postAnswersUrl(
                  wrappers.simulationWrapper().get().getId(),
                  wrappers.categoryWrapper().get().getId(),
                  wrappers.questionWrapper().get().getId(),
                  wrappers.otherUserWrapper().get().getId());
          createUrlAccessToken(
                  wrappers.simulationWrapper().get(), wrappers.userWrapper().get(), url)
              .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      post(url)
                          .content(
                              """
                              {
                                "lessons_answer_score":80,
                                "lessons_answer_positive":"test",
                                "lessons_answer_negative":"test"
                              }
                              """)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString)
              .node("lessons_answer_user")
              .isEqualTo(wrappers.userWrapper().get().getId());
        }

        @Test
        @DisplayName(
            "Given user A has no answers, pass User A cookie and no id query string, then empty response")
        public void given_noSpecificUserIdOnTheQueryString_200OK() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          String url = getAnswersUrl(wrappers.simulationWrapper().get().getId());
          // create extra user
          UserComposer.Composer extraUserWrapper =
              userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
          createUrlAccessToken(wrappers.simulationWrapper().get(), extraUserWrapper.get(), url)
              .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      get(url)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString).isEqualTo("[]");
        }

        @Test
        @DisplayName(
            "Given user A has answers, pass User A cookie and no id query string, then all answers in response belong to user A")
        public void given_noSpecificUserIdOnTheQueryStringButExistingAnswers_200OK()
            throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          String url = getAnswersUrl(wrappers.simulationWrapper().get().getId());
          UrlAccessTokenComposer.Composer tokenWrapper =
              createUrlAccessToken(
                      wrappers.simulationWrapper().get(), wrappers.userWrapper().get(), url)
                  .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      get(url)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString)
              .isArray()
              .allSatisfy(
                  node -> {
                    assertThatJson(node)
                        .node("lessons_answer_user")
                        .isEqualTo(tokenWrapper.get().getUser().getId());
                  });
        }

        @Test
        @DisplayName(
            "Given user A has no answers, pass User A cookie and User A id query string, then empty response")
        public void given_specificOtherUserIdOnTheQueryString_200OK() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          // create extra user
          UserComposer.Composer extraUserWrapper =
              userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
          String url =
              getAnswersUrl(
                  wrappers.simulationWrapper().get().getId(), extraUserWrapper.get().getId());
          createUrlAccessToken(wrappers.simulationWrapper().get(), extraUserWrapper.get(), url)
              .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      get(url)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString).isEqualTo("[]");
        }

        @Test
        @DisplayName(
            "Given user A has no answers, user B has answers, pass User A cookie and User B id query string, then empty response")
        public void given_specificSameUserIdOnTheQueryString_200OK() throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          // create extra user
          UserComposer.Composer extraUserWrapper =
              userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
          String url =
              getAnswersUrl(
                  wrappers.simulationWrapper().get().getId(), wrappers.userWrapper().get().getId());
          createUrlAccessToken(wrappers.simulationWrapper().get(), extraUserWrapper.get(), url)
              .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      get(url)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString).isEqualTo("[]");
        }

        @Test
        @DisplayName(
            "Given user A has answers, pass User A cookie and User A id query string, then all answers in response belong to user A")
        public void given_specificSameUserIdOnTheQueryStringAndCorrespondingCookie_200OK()
            throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          String url =
              getAnswersUrl(
                  wrappers.simulationWrapper().get().getId(), wrappers.userWrapper().get().getId());
          UrlAccessTokenComposer.Composer tokenWrapper =
              createUrlAccessToken(
                      wrappers.simulationWrapper().get(), wrappers.userWrapper().get(), url)
                  .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      get(url)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString)
              .isArray()
              .allSatisfy(
                  node -> {
                    assertThatJson(node)
                        .node("lessons_answer_user")
                        .isEqualTo(tokenWrapper.get().getUser().getId());
                  });
        }

        @Test
        @DisplayName(
            "Given user A has answers, user B has no answers, pass User A cookie and User B id query string, then all answers in response belong to user A")
        public void given_specificOtherUserIdOnTheQueryStringAndDifferentUserCookie_200OK()
            throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          // create extra user
          UserComposer.Composer extraUserWrapper =
              userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
          String url =
              getAnswersUrl(
                  wrappers.simulationWrapper().get().getId(), extraUserWrapper.get().getId());
          UrlAccessTokenComposer.Composer tokenWrapper =
              createUrlAccessToken(
                      wrappers.simulationWrapper().get(), wrappers.userWrapper().get(), url)
                  .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      get(url)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString)
              .isArray()
              .allSatisfy(
                  node -> {
                    assertThatJson(node)
                        .node("lessons_answer_user")
                        .isEqualTo(tokenWrapper.get().getUser().getId());
                  });
        }

        @Test
        @DisplayName(
            "Given user A has answers, user B also has answers, pass User A cookie and User B id query string, then all answers in response belong to user A")
        public void
            given_specificOtherUserIdOnTheQueryStringAndDifferentUserCookieWithAnswers_200OK()
                throws Exception {
          PlayerApiObjectWrappers wrappers = getExerciseWrapper();
          String url =
              getAnswersUrl(
                  wrappers.simulationWrapper().get().getId(),
                  wrappers.otherUserWrapper().get().getId());
          UrlAccessTokenComposer.Composer tokenWrapper =
              createUrlAccessToken(
                      wrappers.simulationWrapper().get(), wrappers.userWrapper().get(), url)
                  .persist();
          entityManager.flush();
          entityManager.clear();

          String responseString =
              mvc.perform(
                      get(url)
                          .contentType(MediaType.APPLICATION_JSON)
                          .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, DEFAULT_RAW_TOKEN))
                          .accept(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          assertThatJson(responseString)
              .isArray()
              .allSatisfy(
                  node -> {
                    assertThatJson(node)
                        .node("lessons_answer_user")
                        .isEqualTo(tokenWrapper.get().getUser().getId());
                  });
        }
      }
    }
  }

  @DisplayName("Given valid player input, should create a player successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validPlayerInput_should_createPlayerSuccessfully() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();

    // -- EXECUTE --
    String response =
        mvc.perform(
                post(PLAYER_URI)
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(PLAYER_FIXTURE_FIRSTNAME, JsonPath.read(response, "$.user_firstname"));
    assertEquals(playerInput.getTagIds().getFirst(), JsonPath.read(response, "$.user_tags[0]"));
    assertEquals(playerInput.getOrganizationId(), JsonPath.read(response, "$.user_organization"));
  }

  @DisplayName("Given invalid email in player input, should throw exceptions")
  @Test
  @WithMockUser(isAdmin = true)
  void given_invalidEmailInPlayerInput_should_throwExceptions() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = new PlayerInput();
    playerInput.setEmail("email");

    // -- EXECUTE --
    String response =
        mvc.perform(
                post(PLAYER_URI)
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is4xxClientError())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertTrue(response.contains(EMAIL_FORMAT));
  }

  @DisplayName("Given restricted user, should not allow creation of player")
  @Test
  @WithMockUser
  void given_restrictedUser_should_notAllowPlayerCreation() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();

    // --EXECUTE--
    mvc.perform(
            post(PLAYER_URI)
                .content(asJsonString(playerInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @DisplayName("Given valid player input, should upsert player successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validPlayerInput_should_upsertPlayerSuccessfully() throws Exception {
    // --PREPARE--
    PlayerInput playerInput = buildPlayerInput();
    User user = new User();
    user.setUpdateAttributes(playerInput);
    userRepository.save(user);
    String newFirstname = "updatedFirstname";
    playerInput.setFirstname(newFirstname);

    // -- EXECUTE --
    String response =
        mvc.perform(
                post(PLAYER_URI + "/upsert")
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(newFirstname, JsonPath.read(response, "$.user_firstname"));
  }

  @DisplayName("Given non-existing player input, should upsert successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_nonExistingPlayerInput_should_upsertSuccessfully() throws Exception {
    // --PREPARE--
    PlayerInput playerInput = buildPlayerInput();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(PLAYER_URI + "/upsert")
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(PLAYER_FIXTURE_FIRSTNAME, JsonPath.read(response, "$.user_firstname"));
  }

  @DisplayName("Given valid player ID and input, should update player but never its address")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validPlayerIdAndInput_should_updatePlayerSuccessfully() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();
    User user = new User();
    user.setUpdateAttributes(playerInput);
    userRepository.save(user);
    String originalEmail = user.getEmail();
    String newFirstname = "updatedFirstname";
    playerInput.setFirstname(newFirstname);
    playerInput.setEmail("attacker@example.invalid");

    // --EXECUTE--
    String response =
        mvc.perform(
                put(PLAYER_URI + "/" + user.getId())
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals("updatedFirstname", JsonPath.read(response, "$.user_firstname"));
    assertEquals(originalEmail, JsonPath.read(response, "$.user_email"));
  }

  @DisplayName("Given restricted user, should not allow updating a player")
  @Test
  @WithMockUser
  void given_restrictedUser_should_notAllowPlayerUpdate() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();
    User user = userRepository.findByEmailIgnoreCase(adminEmail).orElseThrow();

    // -- EXECUTE --
    mvc.perform(
            put(PLAYER_URI + "/" + user.getId())
                .content(asJsonString(playerInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @DisplayName("Given valid player ID, should delete player successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validPlayerId_should_deletePlayerSuccessfully() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();
    User user = new User();
    user.setUpdateAttributes(playerInput);
    user = userRepository.save(user);

    // -- EXECUTE --
    mvc.perform(
            delete(PLAYER_URI + "/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // -- ASSERT --
    assertTrue(this.userRepository.findById(user.getId()).isEmpty());
  }

  @DisplayName("Given non-existing player ID, when deleting, then return 400")
  @Test
  @WithMockUser(isAdmin = true)
  void givenNonExistingPlayerId_whenDelete_thenReturnNoContent() throws Exception {
    // -- PREPARE --
    String nonExistingPlayerId = "nonexistent-id";

    // -- EXECUTE & VERIFY --
    mvc.perform(
            delete(PLAYER_URI + "/" + nonExistingPlayerId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  // -- PRIVATE --

  private PlayerInput buildPlayerInput() {
    Organization organization =
        organizationRepository.save(OrganizationFixture.createOrganization());
    Tag tag = tagRepository.save(TagFixture.getTagNoId());
    PlayerInput player = PlayerFixture.createPlayerInput();
    player.setOrganizationId(organization.getId());
    player.setTagIds(List.of(tag.getId()));
    return player;
  }
}
