package io.openaev.database.model;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.utils.RandomUtils;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PauseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.PauseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(PER_CLASS)
@Transactional
public class InjectTest extends IntegrationTest {

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private PauseComposer pauseComposer;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private RandomUtils randomUtils;

  @Nested
  @DisplayName("Sorting tests")
  public class SortingTests {
    @Test
    @DisplayName("Inject sorting is stable")
    public void stableSort() {
      List<InjectComposer.Composer> injectsCollectionA =
          IntStream.range(1, 500)
              .mapToObj(
                  it ->
                      injectComposer.forInject(
                          () -> {
                            Inject i = InjectFixture.getDefaultInject();
                            i.setDependsDuration((long) it % 10);
                            return i;
                          }))
              .toList();
      List<InjectComposer.Composer> injectsCollectionB =
          IntStream.range(1, 1000)
              .mapToObj(it -> injectComposer.forInject(InjectFixture.getDefaultInject()))
              .toList();
      ExerciseComposer.Composer exerciseWrapper =
          exerciseComposer
              .forExercise(
                  () -> {
                    Exercise exercise = ExerciseFixture.createDefaultExercise();
                    exercise.setStart(Instant.parse("2022-10-04T05:03:57Z"));
                    return exercise;
                  })
              .withInjects(injectsCollectionA)
              .withInjects(injectsCollectionB);
      scenarioComposer
          .forScenario(ScenarioFixture.getScenario())
          .withInjects(injectsCollectionA)
          .withSimulation(exerciseWrapper)
          .persist();

      entityManager.flush();
      entityManager.clear();

      Optional<Exercise> refetched = exerciseRepository.findById(exerciseWrapper.get().getId());

      assertThatNoException().isThrownBy(() -> refetched.get().getInjects());
    }
  }

  @Nested
  @DisplayName("Given valid exercise")
  public class GivenValidExerciseWithTwoPauses {

    private final Instant exerciseStartTime = Instant.parse("2012-11-21T04:00:00Z");

    public ExerciseComposer.Composer getExerciseComposer() {
      return exerciseComposer
          .forExercise(ExerciseFixture.createRunningAttackExercise(exerciseStartTime))
          .withInject(
              injectComposer.forInject(
                  InjectFixture.getDefaultInjectWithDuration(600L))); // run time 04:10
    }

    @Nested
    @DisplayName("With two pauses, one of which starts after original inject time")
    public class WithTwoPauses {

      private final Instant firstPauseStartTime = Instant.parse("2012-11-21T04:02:00Z");
      private final Instant secondPauseStartTime = Instant.parse("2012-11-21T04:15:00Z");

      @Test
      @DisplayName(
          "When the inject was affected by both pauses its starting date should account for both pauses")
      public void WhenInjectEffectivelyWasPausedTwice_InjectDateAccountsForBothPauses() {
        Instant now = Instant.now();
        Exercise exercise =
            getExerciseComposer()
                .withPause(
                    pauseComposer.forPause(
                        // first pause duration brings wakeup close to second pause start
                        // so that inject does not run in between
                        PauseFixture.createPause(firstPauseStartTime, 600L))) // wakeup 04:12
                .withPause(
                    pauseComposer.forPause(
                        PauseFixture.createPause(secondPauseStartTime, 3600L))) // wakeup 05:15
                .get();
        Instant expected_instant = Instant.parse("2012-11-21T05:20:00Z");

        Inject inject = exercise.getInjects().getFirst();

        Assertions.assertTrue(inject.getDate(now).isPresent(), "Inject has no date.");
        Assertions.assertEquals(expected_instant, inject.getDate(now).get());
      }

      @Test
      @DisplayName(
          "When the inject was affected by first pause only its starting date should account for first pause only")
      public void WhenInjectEffectivelyWasPausedOnce_InjectDateAccountsForSinglePause() {
        Instant now = Instant.now();
        Exercise exercise =
            getExerciseComposer()
                .withPause(
                    pauseComposer.forPause(
                        // first pause is short enough so that inject runs before second pause
                        // the pause will effectively delay inject by a single minute
                        PauseFixture.createPause(
                            firstPauseStartTime, 15L))) // wakeup 04:02:15, effective 04:03:00
                .withPause(
                    pauseComposer.forPause(
                        PauseFixture.createPause(secondPauseStartTime, 3600L))) // wakeup 05:15
                .get();
        Instant expected_instant = Instant.parse("2012-11-21T04:11:00Z");

        Inject inject = exercise.getInjects().getFirst();

        Assertions.assertTrue(inject.getDate(now).isPresent(), "Inject has no date.");
        Assertions.assertEquals(expected_instant, inject.getDate(now).get());
      }
    }
  }
}
