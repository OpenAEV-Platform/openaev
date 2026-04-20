package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(PER_CLASS)
@Transactional
class SimulationInjectServiceTest extends IntegrationTest {

  @Autowired private SimulationInjectService simulationInjectService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseRepository exerciseRepository;

  private Exercise simulationA;
  private Exercise simulationB;
  private Inject injectInA;
  private Inject injectInB;

  @BeforeEach
  void setUp() {
    simulationA = exerciseRepository.save(ExerciseFixture.createDefaultExercise());
    simulationB = exerciseRepository.save(ExerciseFixture.createDefaultExercise());

    Inject injectA = InjectFixture.getDefaultInject();
    injectA.setExercise(simulationA);
    injectInA = injectRepository.save(injectA);

    Inject injectB = InjectFixture.getDefaultInject();
    injectB.setExercise(simulationB);
    injectInB = injectRepository.save(injectB);
  }

  // -- READ --

  @Nested
  class FindInjectForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToSimulation_should_returnInject() {
      // -- ACT --
      Inject result =
          simulationInjectService.findInjectForSimulation(simulationA.getId(), injectInA.getId());

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(injectInA.getId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationInjectService.findInjectForSimulation(
                      simulationA.getId(), injectInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }
}
