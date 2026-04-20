package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
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
  @Autowired private ScenarioRepository scenarioRepository;

  private Scenario scenarioA;
  private Scenario scenarioB;
  private Inject injectInA;
  private Inject injectInB;

  @BeforeEach
  void setUp() {
    scenarioA = scenarioRepository.save(ScenarioFixture.createDefaultCrisisScenario());
    scenarioB = scenarioRepository.save(ScenarioFixture.createDefaultCrisisScenario());

    Inject injectA = InjectFixture.getDefaultInject();
    injectA.setScenario(scenarioA);
    injectInA = injectRepository.save(injectA);

    Inject injectB = InjectFixture.getDefaultInject();
    injectB.setScenario(scenarioB);
    injectInB = injectRepository.save(injectB);
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
}
