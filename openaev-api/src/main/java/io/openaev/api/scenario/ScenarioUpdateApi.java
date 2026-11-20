package io.openaev.api.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.catalog.CatalogFeature;
import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioVersionOutput;
import io.openaev.api.scenario.dto.ScenarioChangeOutput;
import io.openaev.api.scenario.dto.ScenarioRevisionOutput;
import io.openaev.api.scenario.dto.ScenarioUpdateInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.catalog.scenario.update.CatalogScenarioUpdateService;
import io.openaev.service.catalog.scenario.update.CatalogScenarioUpdater;
import io.openaev.service.catalog.scenario.update.ScenarioRevisionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Updates a catalogue scenario to another version, or rolls it back to a revision. */
@RestController
@RequiredArgsConstructor
public class ScenarioUpdateApi {

  private final CatalogScenarioUpdater catalogScenarioUpdater;
  private final ScenarioRevisionService scenarioRevisionService;
  private final CatalogScenarioUpdateService catalogScenarioUpdateService;
  private final CatalogItemMapper catalogItemMapper;
  private final CatalogFeature catalogFeature;

  // -- READ --

  @Operation(summary = "Revisions of a catalogue scenario, newest first")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/revisions")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<ScenarioRevisionOutput> revisions(TxCtx ctx, @PathVariable String scenarioId) {
    catalogFeature.require();
    return scenarioRevisionService.revisions(ctx, scenarioId).stream()
        .map(ScenarioUpdateMapper::toOutput)
        .toList();
  }

  @Operation(summary = "Catalogue versions offered after the installed one, newest first")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/newer-versions")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<CatalogScenarioVersionOutput> newerVersions(
      TxCtx ctx, @PathVariable String scenarioId) {
    catalogFeature.require();
    return catalogScenarioUpdateService.newerVersions(ctx, scenarioId).stream()
        .map(catalogItemMapper::toVersionOutput)
        .toList();
  }

  @LogExecutionTime
  @Operation(
      summary = "What updating to a catalogue version changes",
      description =
          "The scenario's own changes stay; a field both changed is a conflict to choose.")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/versions/{version}/update-preview")
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<ScenarioChangeOutput> previewUpdate(
      TxCtx ctx, @PathVariable String scenarioId, @PathVariable String version) {
    catalogFeature.require();
    return catalogScenarioUpdater.previewUpdate(ctx, scenarioId, version).changes().stream()
        .map(ScenarioUpdateMapper::toOutput)
        .toList();
  }

  @LogExecutionTime
  @Operation(summary = "What rolling back to a revision changes")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/revisions/{revisionId}/rollback-preview")
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<ScenarioChangeOutput> previewRollback(
      TxCtx ctx, @PathVariable String scenarioId, @PathVariable String revisionId) {
    catalogFeature.require();
    return catalogScenarioUpdater.previewRollback(ctx, scenarioId, revisionId).changes().stream()
        .map(ScenarioUpdateMapper::toOutput)
        .toList();
  }

  // -- UPDATE --

  @LogExecutionTime
  @Operation(
      summary = "Update a catalogue scenario to a version",
      description = "Refused while a conflict has no side chosen. Keeps a revision first.")
  @PostMapping(TENANT_SCENARIO_URI + "/{scenarioId}/versions/{version}/update")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void update(
      TxCtx ctx,
      @PathVariable String scenarioId,
      @PathVariable String version,
      @Valid @RequestBody ScenarioUpdateInput input) {
    catalogFeature.require();
    catalogScenarioUpdater.update(ctx, scenarioId, version, input.choices());
  }

  @LogExecutionTime
  @Operation(
      summary = "Roll a catalogue scenario back to a revision",
      description = "Refused while a conflict has no side chosen. Keeps a revision first.")
  @PostMapping(TENANT_SCENARIO_URI + "/{scenarioId}/revisions/{revisionId}/rollback")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void rollback(
      TxCtx ctx,
      @PathVariable String scenarioId,
      @PathVariable String revisionId,
      @Valid @RequestBody ScenarioUpdateInput input) {
    catalogFeature.require();
    catalogScenarioUpdater.rollback(ctx, scenarioId, revisionId, input.choices());
  }
}
