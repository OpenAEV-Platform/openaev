package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.form.InjectUpdateActivationInput;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(PER_CLASS)
@Transactional
class ScenarioInjectServiceTest extends IntegrationTest {

  @Autowired private ScenarioInjectService scenarioInjectService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;

  private Scenario scenarioA;
  private Scenario scenarioB;
  private Inject injectInA;
  private Inject injectInB;

  @BeforeEach
  void setUp() {
    InjectComposer.Composer injectComposerA =
        injectComposer.forInject(InjectFixture.getDefaultInject());
    InjectComposer.Composer injectComposerB =
        injectComposer.forInject(InjectFixture.getDefaultInject());

    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultCrisisScenario())
        .withInject(injectComposerA)
        .persist();
    scenarioA = injectComposerA.get().getScenario();
    injectInA = injectComposerA.get();

    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultCrisisScenario())
        .withInject(injectComposerB)
        .persist();
    scenarioB = injectComposerB.get().getScenario();
    injectInB = injectComposerB.get();
  }

  // -- READ --

  @Nested
  class FindInjectForScenario {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToScenario_should_returnInject() {
      // -- ACT --
      Inject result =
          scenarioInjectService.findInjectForScenario(scenarioA.getId(), injectInA.getId());

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(injectInA.getId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherScenario_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  scenarioInjectService.findInjectForScenario(scenarioA.getId(), injectInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  // -- UPDATE --

  @Nested
  class UpdateInjectForScenario {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToScenario_should_updateAndReturnInject() {
      // -- ARRANGE --
      InjectInput input = new InjectInput();
      input.setTitle("Updated title");
      input.setDependsDuration(0L);

      // -- ACT --
      Inject result =
          scenarioInjectService.updateInjectForScenario(
              scenarioA.getId(), injectInA.getId(), input);

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(injectInA.getId());
      assertThat(result.getTitle()).isEqualTo("Updated title");
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherScenario_should_throwElementNotFoundException() {
      // -- ARRANGE --
      InjectInput input = new InjectInput();
      input.setTitle("Updated title");
      input.setDependsDuration(0L);

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  scenarioInjectService.updateInjectForScenario(
                      scenarioA.getId(), injectInB.getId(), input))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  class UpdateInjectActivationForScenario {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToScenario_should_updateActivationAndReturnInject() {
      // -- ARRANGE --
      InjectUpdateActivationInput input = new InjectUpdateActivationInput();
      input.setEnabled(false);

      // -- ACT --
      Inject result =
          scenarioInjectService.updateInjectActivationForScenario(
              scenarioA.getId(), injectInA.getId(), input);

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(injectInA.getId());
      assertThat(result.isEnabled()).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherScenario_should_throwElementNotFoundException() {
      // -- ARRANGE --
      InjectUpdateActivationInput input = new InjectUpdateActivationInput();
      input.setEnabled(false);

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  scenarioInjectService.updateInjectActivationForScenario(
                      scenarioA.getId(), injectInB.getId(), input))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  // -- DELETE --

  @Nested
  class DeleteInject {

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToScenario_should_deleteInject() {
      // -- ACT --
      scenarioInjectService.deleteInject(scenarioA.getId(), injectInA.getId());

      // -- ASSERT --
      assertThat(injectRepository.findById(injectInA.getId())).isEmpty();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_injectBelongsToAnotherScenario_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () -> scenarioInjectService.deleteInject(scenarioA.getId(), injectInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }
}
