package io.openaev.database.model;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
class SimulationTest extends IntegrationTest {
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private EntityManager entityManager;

  private final Instant exerciseStartTime = Instant.parse("2012-11-21T04:00:00Z");

  @Test
  @DisplayName("Given a persisted exercise, current pause from raw query is correctly persisted.")
  void GivenAnExercise_CurrentPauseFromRawQueryIsCorrectlyPersisted() {
    Instant expectedCurrentPauseTime = Instant.parse("2012-11-21T04:05:00Z");
    ExerciseComposer.Composer wrapper =
        exerciseComposer.forExercise(
            ExerciseFixture.createDefaultAttackExercise(exerciseStartTime));
    wrapper.get().setCurrentPause(expectedCurrentPauseTime); // current pause at T+5 minutes

    Exercise expected = wrapper.persist().get();

    // reset JPA
    entityManager.flush();
    entityManager.clear();

    Exercise dbExercise = exerciseRepository.findById(expected.getId()).orElseThrow();

    Assertions.assertTrue(
        expected.getCurrentPause().isPresent(),
        "Current pause should be present for expected exercise");
    Assertions.assertEquals(expected.getCurrentPause().get(), dbExercise.getCurrentPause().get());
  }
}
