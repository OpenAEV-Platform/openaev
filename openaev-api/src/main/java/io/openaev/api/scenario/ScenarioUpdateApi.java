package io.openaev.api.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.scenario.dto.ScenarioRevisionOutput;
import io.openaev.api.scenario.dto.ScenarioUpdateInput;
import io.openaev.api.scenario.dto.ScenarioUpdatePreviewOutput;
import io.openaev.api.scenario.dto.ScenarioVersionsOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.catalog.scenario.update.CatalogScenarioUpdater;
import io.openaev.service.catalog.scenario.update.InstalledScenarioService;
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
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class ScenarioUpdateApi {

  private final CatalogScenarioUpdater catalogScenarioUpdater;
  private final ScenarioRevisionService scenarioRevisionService;
  private final InstalledScenarioService installedScenarioService;

  // -- READ --

  @Operation(summary = "Revisions of a catalogue scenario, newest first")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/revisions")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<ScenarioRevisionOutput> revisions(TxCtx ctx, @PathVariable String scenarioId) {
    return scenarioRevisionService.revisions(ctx, scenarioId).stream()
        .map(ScenarioUpdateMapper::toOutput)
        .toList();
  }

  @Operation(summary = "Catalogue versions of a scenario, newest first")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/versions")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioVersionsOutput versions(TxCtx ctx, @PathVariable String scenarioId) {
    return ScenarioUpdateMapper.toOutput(installedScenarioService.versions(ctx, scenarioId));
  }

  @LogExecutionTime
  @Operation(
      summary = "What updating to a catalogue version changes",
      description =
          "The scenario's own changes stay; a field both changed is a conflict to choose.")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/versions/{version}/preview")
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioUpdatePreviewOutput previewUpdate(
      TxCtx ctx, @PathVariable String scenarioId, @PathVariable String version) {
    return ScenarioUpdateMapper.toOutput(
        catalogScenarioUpdater.previewUpdate(ctx, scenarioId, version));
  }

  @LogExecutionTime
  @Operation(summary = "What rolling back to a revision changes")
  @GetMapping(TENANT_SCENARIO_URI + "/{scenarioId}/revisions/{revisionId}/preview")
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioUpdatePreviewOutput previewRollback(
      TxCtx ctx, @PathVariable String scenarioId, @PathVariable String revisionId) {
    return ScenarioUpdateMapper.toOutput(
        catalogScenarioUpdater.previewRollback(ctx, scenarioId, revisionId));
  }

  // -- UPDATE --

  @LogExecutionTime
  @Operation(
      summary = "Update a catalogue scenario to a version",
      description = "Refused while a conflict has no side chosen. Keeps a revision first.")
  @PostMapping(TENANT_SCENARIO_URI + "/{scenarioId}/versions/{version}/updates")
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
    catalogScenarioUpdater.update(ctx, scenarioId, version, input.choices(), input.edits());
  }

  @LogExecutionTime
  @Operation(
      summary = "Roll a catalogue scenario back to a revision",
      description = "Refused while a conflict has no side chosen. Keeps a revision first.")
  @PostMapping(TENANT_SCENARIO_URI + "/{scenarioId}/revisions/{revisionId}/rollbacks")
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
    catalogScenarioUpdater.rollback(ctx, scenarioId, revisionId, input.choices(), input.edits());
  }
}
