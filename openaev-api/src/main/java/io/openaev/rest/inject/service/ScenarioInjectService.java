package io.openaev.rest.inject.service;

import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.form.InjectUpdateActivationInput;
import io.openaev.service.scenario.ScenarioService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class ScenarioInjectService {

  private final ScenarioService scenarioService;
  private final InjectService injectService;
  private final InjectRepository injectRepository;

  // -- READ --

  /** Finds an inject that belongs to the given scenario. */
  public Inject findInjectForScenario(
      @NotBlank final String scenarioId, @NotBlank final String injectId) {
    return injectRepository
        .findByIdAndScenarioId(injectId, scenarioId)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Inject not found with id: " + injectId + " in scenario: " + scenarioId));
  }

  // -- UPDATE --

  /** Updates an inject that belongs to the given scenario. */
  public Inject updateInjectForScenario(
      @NotBlank final String scenarioId,
      @NotBlank final String injectId,
      @NotNull InjectInput input) {
    checkInjectForScenario(scenarioId, injectId);
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    Inject inject = injectService.updateInject(injectId, input);

    // It should not be possible to add EE executor on inject when the scenario is already
    // scheduled.
    if (scenario.getRecurrenceStart() != null) {
      this.injectService.throwIfInjectNotLaunchable(inject);
    }

    // If Documents not yet linked directly to the scenario, attach them
    inject
        .getDocuments()
        .forEach(
            document -> {
              if (!document.getDocument().getScenarios().contains(scenario)) {
                scenario.getDocuments().add(document.getDocument());
              }
            });
    this.scenarioService.updateScenario(scenario);
    return injectRepository.save(inject);
  }

  /** Toggles the activation of an inject that belongs to the given scenario. */
  public Inject updateInjectActivationForScenario(
      @NotBlank final String scenarioId,
      @NotBlank final String injectId,
      InjectUpdateActivationInput input) {
    checkInjectForScenario(scenarioId, injectId);
    return injectService.updateInjectActivation(injectId, input);
  }

  // -- DELETE --

  /** Deletes an inject that belongs to the given scenario. */
  public void deleteInject(@NotBlank final String scenarioId, @NotBlank final String injectId) {
    checkInjectForScenario(scenarioId, injectId);
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    this.injectService.delete(injectId);
    this.scenarioService.updateScenario(scenario);
  }

  // -- PRIVATE --

  /** Checks that an inject belongs to the given scenario. Throws if not found. */
  private void checkInjectForScenario(
      @NotBlank final String scenarioId, @NotBlank final String injectId) {
    if (!injectRepository.existsByIdAndScenarioId(injectId, scenarioId)) {
      throw new ElementNotFoundException(
          "Inject not found with id: " + injectId + " in scenario: " + scenarioId);
    }
  }
}
