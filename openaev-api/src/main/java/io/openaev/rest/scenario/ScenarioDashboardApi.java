package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.ResourceType;
import io.openaev.engine.query.*;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.utils.es.EntitiesPaginationInput;
import io.openaev.utils.es.WidgetToEntitiesInput;
import io.openaev.utils.es.WidgetToEntitiesOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioDashboardApi {

  private final CustomDashboardService customDashboardService;

  @Operation(summary = "Find the dashboard linked to a Scenario")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The dashboard"),
        @ApiResponse(responseCode = "404", description = "The Scenario doesn't exist")
      })
  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/dashboard",
    TENANT_SCENARIO_URI + "/{scenarioId}/dashboard"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ResponseEntity<CustomDashboard> dashboard(@PathVariable final String scenarioId) {
    return ResponseEntity.ok(
        this.customDashboardService.findCustomDashboardByResourceId(scenarioId));
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/dashboard/count/{widgetId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/dashboard/count/{widgetId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public EsCountInterval dashboardCount(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardCountOnResourceId(scenarioId, widgetId, parameters);
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/dashboard/average/{widgetId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/dashboard/average/{widgetId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public EsAvgs dashboardAverage(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardAverageOnResourceId(
        scenarioId, widgetId, parameters);
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/dashboard/series/{widgetId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/dashboard/series/{widgetId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<EsSeries> dashboardSeries(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardSeriesOnResourceId(
        scenarioId, widgetId, parameters);
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/dashboard/entities/{widgetId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/dashboard/entities/{widgetId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public EsEntities dashboardEntities(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @RequestBody EntitiesPaginationInput input) {
    return this.customDashboardService.dashboardEntitiesOnResourceId(scenarioId, widgetId, input);
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/dashboard/entities-runtime/{widgetId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/dashboard/entities-runtime/{widgetId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @Valid @RequestBody(required = false) WidgetToEntitiesInput input) {
    return this.customDashboardService.widgetToEntitiesRuntimeOnResourceId(
        scenarioId, widgetId, input);
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/dashboard/attack-paths/{widgetId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/dashboard/attack-paths/{widgetId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(summary = "Search TagRules")
  public List<EsAttackPath> dashboardAttackPaths(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return this.customDashboardService.dashboardAttackPathsOnResourceId(
        scenarioId, widgetId, parameters);
  }
}
