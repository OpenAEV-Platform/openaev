package io.openaev.rest.exercise.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
class ExerciseServiceTest extends IntegrationTest {

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private ExerciseService actualExerciseService;

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    exerciseComposer.reset();
  }

  @Nested
  @DisplayName("Tests for latest validity date")
  public class LatestValidityDate {
    @Nested
    @DisplayName("With recurring scenario")
    public class WithRecurringScenario {
      private ScenarioComposer.Composer scenarioWrapper;

      @BeforeEach
      public void setup() {
        scenarioWrapper =
            scenarioComposer.forScenario(
                ScenarioFixture.getScenarioWithRecurrence("56 43 10 * * *"));
      }

      @Nested
      @DisplayName("With successor Simulation")
      public class WithSuccessorSimulation {
        private ExerciseComposer.Composer successorSimulationWrapper;
        private final Instant successorSimulationStartTime = Instant.parse("2022-04-24T01:02:03Z");

        @BeforeEach
        public void setup() {
          Exercise successorFixture = ExerciseFixture.createDefaultExercise();
          successorFixture.setStart(successorSimulationStartTime);
          successorSimulationWrapper = exerciseComposer.forExercise(successorFixture);
        }

        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until successor start date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(successorSimulationStartTime);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }

      @Nested
      @DisplayName("Without successor Simulation")
      public class WithoutSuccessorSimulation {
        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until next scenario trigger")
          public void isValidUntilNextScenarioTrigger() {
            scenarioWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Instant expected = Instant.parse("2022-04-23T10:43:56Z");

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(expected);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }
    }

    @Nested
    @DisplayName("With non-recurring scenario")
    public class WithNonRecurringScenario {
      private ScenarioComposer.Composer scenarioWrapper;

      @BeforeEach
      public void setup() {
        scenarioWrapper = scenarioComposer.forScenario(ScenarioFixture.getScenario());
      }

      @Nested
      @DisplayName("With successor Simulation")
      public class WithSuccessorSimulation {
        private ExerciseComposer.Composer successorSimulationWrapper;
        private final Instant successorSimulationStartTime = Instant.parse("2022-04-24T01:02:03Z");

        @BeforeEach
        public void setup() {
          Exercise successorFixture = ExerciseFixture.createDefaultExercise();
          successorFixture.setStart(successorSimulationStartTime);
          successorSimulationWrapper = exerciseComposer.forExercise(successorFixture);
        }

        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until successor start date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(successorSimulationStartTime);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }

      @Nested
      @DisplayName("Without successor Simulation")
      public class WithoutSuccessorSimulation {
        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilNextScenarioTrigger() {
            scenarioWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }
    }
  }
}
