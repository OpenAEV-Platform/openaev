package io.openaev.rest;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.lessons.form.LessonsSendInput;
import io.openaev.service.MailingService;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonUtils.asJsonString;
import static io.openaev.utils.fixtures.ExerciseFixture.getExercise;
import static io.openaev.utils.fixtures.ExerciseLessonsCategoryFixture.getLessonsCategory;
import static io.openaev.utils.fixtures.TeamFixture.getTeam;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
public class ExerciseLessonsApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseService exerciseService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @MockitoSpyBean private MailingService mailingService;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;

  private LessonsCategory getLessonCategory() {
    User user = this.userRepository.save(getUser());
    Team team = teamRepository.save(getTeam(user, "My team", false));
    Exercise simulation = this.exerciseService.createExercise(getExercise(List.of(team)));
    return this.lessonsCategoryRepository.save(getLessonsCategory(simulation, List.of(team)));
  }

  @DisplayName("Send surveys for exercise lessons")
  @Test
  @WithMockUser(isAdmin = true)
  void sendExerciseLessonsTest() throws Exception {

    // -- PREPARE --
    LessonsCategory lessonsCategory = getLessonCategory();

    String lessonSubject = "Subject";
    String lessonBody = "This is a lesson";
    LessonsSendInput lessonsSendInput = new LessonsSendInput();
    lessonsSendInput.setSubject(lessonSubject);
    lessonsSendInput.setBody(lessonBody);
    User user = userRepository.findById(lessonsCategory.getUsers().getFirst()).orElseThrow();

    // -- EXECUTE --
    mvc.perform(
        post(EXERCISE_URI + "/" + lessonsCategory.getExercise().getId() + "/lessons_send")
          .content(asJsonString(lessonsSendInput))
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON))
      .andExpect(status().is2xxSuccessful());

    // -- ASSERT --
    verify(mailingService)
      .sendEmail(
        lessonSubject,
        lessonBody,
        List.of(user),
        exerciseRepository.findById(lessonsCategory.getExercise().getId()));
  }
}
