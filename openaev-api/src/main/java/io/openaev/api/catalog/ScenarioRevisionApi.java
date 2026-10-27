package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.CATALOG_URI;
import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.scenario.revision.ScenarioApplyService;
import io.openaev.service.scenario.revision.ScenarioMerge.Choice;
import io.openaev.service.scenario.revision.ScenarioRevisionService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Update and rollback of an installed catalogue scenario, and its revisions (ADR-018). */
@RestController
@RequiredArgsConstructor
public class ScenarioRevisionApi {

  private static final String PATH = "/installed-scenarios/{scenarioId}";

  private final ScenarioApplyService scenarioApplyService;
  private final ScenarioRevisionService scenarioRevisionService;

  @LogExecutionTime
  @Operation(summary = "Revisions of an installed catalogue scenario, newest first")
  @GetMapping({CATALOG_URI + PATH + "/revisions", TENANT_CATALOG_URI + PATH + "/revisions"})
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<ScenarioRevisionOutput> revisions(TxCtx ctx, @PathVariable String scenarioId) {
    return scenarioRevisionService.findAll(scenarioId).stream()
        .map(ScenarioRevisionOutput::of)
        .toList();
  }

  @LogExecutionTime
  @Operation(
      summary = "Preview an update to a release, or a rollback to a revision",
      description = "What changes, what the client changed, and the conflicts to choose.")
  @PostMapping({
    CATALOG_URI + PATH + "/update-preview",
    TENANT_CATALOG_URI + PATH + "/update-preview"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioUpdatePreviewOutput preview(
      TxCtx ctx, @PathVariable String scenarioId, @RequestBody ScenarioUpdateInput input) {
    return ScenarioUpdatePreviewOutput.of(
        scenarioApplyService.preview(scenarioId, target(input), choices(input), edits(input)));
  }

  @LogExecutionTime
  @Operation(
      summary = "Apply an update or a rollback",
      description =
          "Refused while a conflict has no choice, or when the scenario changed since the"
              + " preview. A revision of the current content is taken first.")
  @PostMapping({CATALOG_URI + PATH + "/update", TENANT_CATALOG_URI + PATH + "/update"})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public ScenarioUpdatePreviewOutput apply(
      TxCtx ctx, @PathVariable String scenarioId, @RequestBody ScenarioUpdateInput input)
      throws Exception {
    return ScenarioUpdatePreviewOutput.of(
        scenarioApplyService.apply(
            ctx, scenarioId, target(input), choices(input), edits(input), input.localHash()));
  }

  private static ScenarioApplyService.Target target(ScenarioUpdateInput input) {
    if ((input.version() == null) == (input.revisionId() == null)) {
      throw new BadRequestException("Give a version or a revision");
    }
    return new ScenarioApplyService.Target(input.version(), input.revisionId());
  }

  private static Map<String, Choice> choices(ScenarioUpdateInput input) {
    return input.choices() == null ? Map.of() : input.choices();
  }

  private static Map<String, JsonNode> edits(ScenarioUpdateInput input) {
    return input.edits() == null ? Map.of() : input.edits();
  }
}
