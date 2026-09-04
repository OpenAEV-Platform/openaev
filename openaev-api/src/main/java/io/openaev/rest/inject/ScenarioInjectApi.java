package io.openaev.rest.inject;

import static io.openaev.database.specification.InjectSpecification.fromScenario;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.*;
import io.openaev.rest.inject.output.InjectOutput;
import io.openaev.rest.inject.service.InjectAssistantService;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.ScenarioInjectService;
import io.openaev.rest.kill_chain_phase.KillChainPhaseInitializer;
import io.openaev.service.*;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.criteria.Join;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioInjectApi extends RestBehavior {

  private final InjectAssistantService injectAssistantService;
  private final InjectSearchService injectSearchService;
  private final InjectRepository injectRepository;
  private final ScenarioService scenarioService;
  private final InjectService injectService;
  private final InjectDuplicateService injectDuplicateService;
  private final ScenarioInjectService scenarioInjectService;
  private final InjectMapper injectMapper;
  private final BulkInjectService bulkInjectService;

  // -- READ --

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/injects/simple",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/simple"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> scenarioInjectsSimple(
      TxCtx ctx, @PathVariable @NotBlank final String scenarioId) {
    return injectSearchService.injects(fromScenario(scenarioId));
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/injects/simple",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/simple"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> scenarioInjectsSimple(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<Inject> specification,
            Specification<Inject> specificationCount,
            Pageable pageable) ->
            this.injectSearchService.injects(
                fromScenario(scenarioId).and(specification),
                fromScenario(scenarioId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Inject.class,
        joinMap);
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/injects",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Inject> scenarioInjects(
      TxCtx ctx, @PathVariable @NotBlank final String scenarioId) {
    List<Inject> injects =
        this.injectRepository.findByScenarioId(scenarioId).stream()
            .sorted(Inject.executionComparator)
            .toList();
    KillChainPhaseInitializer.initializeFromInjects(injects);
    return injects;
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/injects/{injectId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/{injectId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Inject scenarioInject(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    assert scenarioId.equals(scenario.getId());
    return hydrateKillChainPhases(
        injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new));
  }

  // -- CREATE --

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/injects",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public InjectOutput createInjectForScenario(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set
      // the tenant scope in the DB session so the v2 inspector can resolve can_access_tenant.
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody InjectInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    Inject persistedInject = this.injectService.createAndSaveInject(null, scenario, input);
    return injectMapper.toInjectOutput(persistedInject, injectService.runChecks(persistedInject));
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/injects/bulk",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/bulk"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public List<Inject> createInjectsForScenario(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody List<InjectInput> inputs) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    List<Inject> created = this.injectService.createAndSaveInjectList(null, scenario, inputs);
    KillChainPhaseInitializer.initializeFromInjects(created);
    return created;
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/injects/assistant",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/assistant"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  @Operation(
      summary = "Assistant to generate injects for scenario",
      description = "Generates injects based on the provided attack pattern and targets.")
  public List<Inject> generateInjectsForScenario(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody InjectAssistantInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    List<Inject> generated =
        injectService.saveAll(
            this.injectAssistantService.generateInjectsForScenario(scenario, input));
    KillChainPhaseInitializer.initializeFromInjects(generated);
    return generated;
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/injects/{injectId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/{injectId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public InjectOutput duplicateInjectForScenario(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set
      // the tenant scope in the DB session so the v2 inspector can resolve can_access_tenant.
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId) {
    Inject persistedInject =
        injectDuplicateService.duplicateInjectForScenarioWithDuplicateWordInTitle(
            scenarioId, injectId);
    return injectMapper.toInjectOutput(persistedInject, injectService.runChecks(persistedInject));
  }

  // -- UPDATE --

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/injects/{injectId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/{injectId}"
  })
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public InjectOutput updateInjectForScenario(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set
      // the tenant scope in the DB session so the v2 inspector can resolve can_access_tenant.
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody @NotNull InjectInput input) {
    return scenarioInjectService.updateInjectForScenario(scenarioId, injectId, input);
  }

  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/injects/{injectId}/activation",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/{injectId}/activation"
  })
  @Transactional
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject updateInjectActivationForScenario(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody InjectUpdateActivationInput input) {
    return hydrateKillChainPhases(
        scenarioInjectService.updateInjectActivationForScenario(scenarioId, injectId, input));
  }

  // -- BULK UPDATE --

  @Operation(
      summary = "Bulk update of injects for a scenario",
      description = "Updates in bulk the injects of the given scenario.")
  // SUPPORTS (not REQUIRED) on purpose: the update itself runs in the service's own transaction,
  // wrapped in a massive-operation scope (header progress indicator + per-entity stream event
  // suppression) that must cover the commit-time flush.
  @Transactional(propagation = Propagation.SUPPORTS)
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/injects",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @LogExecutionTime
  public List<Inject> bulkUpdateInjectsForScenario(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid final InjectBulkUpdateInputs input) {
    input.setSimulationOrScenarioId(scenarioId);
    return bulkInjectService.bulkUpdateWithMonitoring(ctx, input);
  }

  // -- BULK DELETE --

  @Operation(
      summary = "Bulk delete of injects for a scenario",
      description = "Deletes in bulk the injects of the given scenario.")
  // SUPPORTS (not REQUIRED) on purpose: the deletion itself runs in the service's own
  // transaction, wrapped in a massive-operation scope (header progress indicator + per-entity
  // stream event suppression) that must cover the commit-time flush.
  @Transactional(propagation = Propagation.SUPPORTS)
  @DeleteMapping({
    SCENARIO_URI + "/{scenarioId}/injects",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @LogExecutionTime
  public List<Inject> bulkDeleteInjectsForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid final InjectBulkProcessingInput input) {
    input.setSimulationOrScenarioId(scenarioId);
    return bulkInjectService.bulkDeleteWithMonitoring(input);
  }

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping({
    SCENARIO_URI + "/{scenarioId}/injects/{injectId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/injects/{injectId}"
  })
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void deleteInjectForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId) {
    this.scenarioInjectService.deleteInject(scenarioId, injectId);
  }

  /** See {@link KillChainPhaseInitializer}: hydrate before open-in-view rendering. */
  private static Inject hydrateKillChainPhases(Inject inject) {
    KillChainPhaseInitializer.initializeFromInjects(List.of(inject));
    return inject;
  }
}
