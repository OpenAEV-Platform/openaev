package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Team;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectTeamsInput;
import io.openaev.rest.inject.form.InjectUpdateActivationInput;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
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
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private TeamComposer teamComposer;

  private Exercise simulationA;
  private Exercise simulationB;
  private Inject injectInA;
  private Inject injectInB;

  @BeforeEach
  void setUp() {
    InjectComposer.Composer injectComposerA =
        injectComposer.forInject(InjectFixture.getDefaultInject());
    InjectComposer.Composer injectComposerB =
        injectComposer.forInject(InjectFixture.getDefaultInject());

    exerciseComposer
        .forExercise(ExerciseFixture.createDefaultExercise())
        .withInject(injectComposerA)
        .persist();
    simulationA = injectComposerA.get().getExercise();
    injectInA = injectComposerA.get();

    exerciseComposer
        .forExercise(ExerciseFixture.createDefaultExercise())
        .withInject(injectComposerB)
        .persist();
    simulationB = injectComposerB.get().getExercise();
    injectInB = injectComposerB.get();
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

  @Nested
  class FindInjectTeamsForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToSimulation_should_returnTeams() {
      // -- ARRANGE --
      TeamComposer.Composer teamWrapper =
          teamComposer.forTeam(TeamFixture.createTeamWithName(null));
      teamWrapper.persist();
      injectInA.setTeams(List.of(teamWrapper.get()));
      injectRepository.save(injectInA);

      // -- ACT --
      List<Team> result =
          simulationInjectService.findInjectTeamsForSimulation(
              simulationA.getId(), injectInA.getId());

      // -- ASSERT --
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getId()).isEqualTo(teamWrapper.get().getId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationInjectService.findInjectTeamsForSimulation(
                      simulationA.getId(), injectInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  // -- UPDATE --

  @Nested
  class UpdateInjectActivationForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToSimulation_should_updateActivationAndReturnInject() {
      // -- ARRANGE --
      InjectUpdateActivationInput input = new InjectUpdateActivationInput();
      input.setEnabled(false);

      // -- ACT --
      Inject result =
          simulationInjectService.updateInjectActivationForSimulation(
              simulationA.getId(), injectInA.getId(), input);

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(injectInA.getId());
      assertThat(result.isEnabled()).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ARRANGE --
      InjectUpdateActivationInput input = new InjectUpdateActivationInput();
      input.setEnabled(false);

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationInjectService.updateInjectActivationForSimulation(
                      simulationA.getId(), injectInB.getId(), input))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  class TriggerInjectForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToSimulation_should_setTriggerNowDateAndReturnInject() {
      // -- ACT --
      Inject result =
          simulationInjectService.triggerInjectForSimulation(
              simulationA.getId(), injectInA.getId());

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(injectInA.getId());
      assertThat(result.getTriggerNowDate()).isNotNull();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationInjectService.triggerInjectForSimulation(
                      simulationA.getId(), injectInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  class UpdateInjectTeamsForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToSimulation_should_updateTeamsAndReturnInject() {
      // -- ARRANGE --
      TeamComposer.Composer teamWrapper =
          teamComposer.forTeam(TeamFixture.createTeamWithName(null));
      teamWrapper.persist();
      InjectTeamsInput input = new InjectTeamsInput();
      input.setTeamIds(List.of(teamWrapper.get().getId()));

      // -- ACT --
      Inject result =
          simulationInjectService.updateInjectTeamsForSimulation(
              simulationA.getId(), injectInA.getId(), input);

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(injectInA.getId());
      assertThat(result.getTeams()).hasSize(1);
      assertThat(result.getTeams().getFirst().getId()).isEqualTo(teamWrapper.get().getId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ARRANGE --
      InjectTeamsInput input = new InjectTeamsInput();
      input.setTeamIds(List.of());

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationInjectService.updateInjectTeamsForSimulation(
                      simulationA.getId(), injectInB.getId(), input))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  // -- DELETE --

  @Nested
  class DeleteInject {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToSimulation_should_deleteInject() {
      // -- ACT --
      simulationInjectService.deleteInject(simulationA.getId(), injectInA.getId());

      // -- ASSERT --
      assertThat(injectRepository.findById(injectInA.getId())).isEmpty();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () -> simulationInjectService.deleteInject(simulationA.getId(), injectInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }
}
