package io.openaev.rest.inject.service;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.form.InjectUpdateActivationInput;
import io.openaev.rest.inject.output.InjectOutput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.mapper.InjectMapper;
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
  private final InjectMapper injectMapper;
  private final PayloadVersionService payloadVersionService;
  private final PreviewFeatureService previewFeatureService;

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

  /**
   * Resolves the inject inside the request's tenant scope and checks that it belongs to the given
   * scenario, or answers not found.
   */
  @Transactional(readOnly = true)
  public void requireInjectInScenario(
      @NotNull final TxCtx ctx,
      @NotBlank final String scenarioId,
      @NotBlank final String injectId) {
    Inject inject = injectService.inject(ctx, injectId);
    if (inject.getScenario() == null || !scenarioId.equals(inject.getScenario().getId())) {
      throw new ElementNotFoundException(
          "Inject not found with id: " + injectId + " in scenario: " + scenarioId);
    }
  }

  // -- UPDATE --

  /** Updates an inject that belongs to the given scenario. */
  public InjectOutput updateInjectForScenario(
      @NotNull final TxCtx ctx,
      @NotBlank final String scenarioId,
      @NotBlank final String injectId,
      @NotNull InjectInput input) {
    checkInjectForScenario(scenarioId, injectId);
    Scenario scenario = this.scenarioService.scenario(ctx, scenarioId);
    Inject inject = injectService.updateInject(injectId, input);
    changePayloadVersion(inject, input.getInjectorContract());

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
    Inject persistedInject = injectRepository.save(inject);
    return injectMapper.toInjectOutput(persistedInject, injectService.runChecks(persistedInject));
  }

  private void changePayloadVersion(Inject inject, String actionId) {
    InjectorContract current = inject.getInjectorContract().orElse(null);
    if (actionId == null
        || current == null
        || current.getPayload() == null
        || actionId.equals(current.getId())
        || !previewFeatureService.isFeatureEnabled(PreviewFeature.CATALOG)) {
      return;
    }
    inject.setInjectorContract(
        payloadVersionService.otherVersionActionById(
            inject.getTenant().getId(), current.getPayload(), actionId));
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
  public void deleteInject(
      @NotNull final TxCtx ctx,
      @NotBlank final String scenarioId,
      @NotBlank final String injectId) {
    deleteInject(ctx, scenarioId, injectId, false);
  }

  /**
   * Deletes an inject that belongs to the given scenario.
   *
   * @param keepFindings true keeps the findings it produced, detached from it; false (the default)
   *     deletes them with the inject
   */
  public void deleteInject(
      @NotNull final TxCtx ctx,
      @NotBlank final String scenarioId,
      @NotBlank final String injectId,
      final boolean keepFindings) {
    checkInjectForScenario(scenarioId, injectId);
    Scenario scenario = this.scenarioService.scenario(ctx, scenarioId);
    this.injectService.delete(injectId, keepFindings);
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
