package io.openaev.service;

import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultCrisisExercise;
import static io.openaev.utils.fixtures.ExerciseTeamUserFixture.createExerciseTeamUser;
import static io.openaev.utils.fixtures.TeamFixture.getDefaultTeam;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseTeamUser;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseTeamUserRepository;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class ExerciseTeamUserServiceTest extends IntegrationTest {

  @Mock private ExerciseTeamUserRepository exerciseTeamUserRepository;

  @InjectMocks private ExerciseTeamUserService exerciseTeamUserService;

  @Captor private ArgumentCaptor<ExerciseTeamUser> teamUserCaptor;

  @Captor private ArgumentCaptor<List<ExerciseTeamUser>> teamUserListCaptor;

  @Test
  void given_validParameters_should_createExerciseTeamUserSuccessfully() {
    // -- ARRANGE --
    Exercise exercise = createDefaultCrisisExercise();
    Team team = getDefaultTeam();
    User user = getUser();

    // -- ACT --
    exerciseTeamUserService.createExerciseTeamUser(exercise, team, user);

    // -- ASSERT --
    verify(exerciseTeamUserRepository).save(teamUserCaptor.capture());
    ExerciseTeamUser captured = teamUserCaptor.getValue();
    assertEquals(exercise, captured.getExercise());
    assertEquals(team, captured.getTeam());
    assertEquals(user, captured.getUser());
  }

  @Test
  void given_multipleSourceTeamUsers_should_duplicateAllForTargetExercise() {
    // -- ARRANGE --
    Exercise sourceExercise = createDefaultCrisisExercise();
    Exercise targetExercise = createDefaultCrisisExercise();
    Team team = getDefaultTeam();
    User user1 = getUser("User1", "Last1", "user1@test.invalid");
    User user2 = getUser("User2", "Last2", "user2@test.invalid");

    ExerciseTeamUser source1 = createExerciseTeamUser(sourceExercise, team, user1);
    ExerciseTeamUser source2 = createExerciseTeamUser(sourceExercise, team, user2);

    // -- ACT --
    exerciseTeamUserService.duplicateTeamUsers(
        targetExercise, List.of(source1, source2), new HashMap<>());

    // -- ASSERT --
    verify(exerciseTeamUserRepository).saveAll(teamUserListCaptor.capture());
    List<ExerciseTeamUser> captured = teamUserListCaptor.getValue();
    assertEquals(2, captured.size());
    captured.forEach(etu -> assertEquals(targetExercise, etu.getExercise()));
    assertEquals(user1, captured.get(0).getUser());
    assertEquals(user2, captured.get(1).getUser());
  }
}
