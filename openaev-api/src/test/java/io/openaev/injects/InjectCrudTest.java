package io.openaev.injects;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class InjectCrudTest extends IntegrationTest {

  @Autowired private InjectRepository injectRepository;

  @Autowired private ExerciseRepository exerciseRepository;

  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorContractFixture injectorContractFixture;

  @DisplayName("Test inject creation with non null depends duration")
  @Test
  void createInjectSuccess() {
    // -- PREPARE --
    Exercise exercise = new Exercise();
    exercise.setName("Exercise name");
    exercise.setFrom("test@test.com");
    exercise.setReplyTos(List.of("test@test.com"));
    Exercise exerciseCreated = this.exerciseRepository.save(exercise);
    Inject inject = new Inject();
    inject.setTitle("test");
    inject.setInjectorContract(injectorContractFixture.getWellKnownSingleEmailContract());
    inject.setExercise(exerciseCreated);
    inject.setDependsDuration(0L);

    // -- EXECUTE --
    Inject injectCreated = this.injectRepository.save(inject);
    assertNotNull(injectCreated);

    // -- CLEAN --
    this.exerciseRepository.delete(exercise);
    this.injectRepository.delete(inject);
  }
}
