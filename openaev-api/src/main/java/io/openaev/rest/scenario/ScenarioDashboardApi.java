package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.ResourceType;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.query.EsAttackPath;
import io.openaev.engine.query.EsAvgs;
import io.openaev.engine.query.EsCountInterval;
import io.openaev.engine.query.EsSeries;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.dashboard.model.WidgetToEntitiesInput;
import io.openaev.rest.dashboard.model.WidgetToEntitiesOutput;
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
  @GetMapping(SCENARIO_URI + "/{scenarioId}/dashboard")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ResponseEntity<CustomDashboard> dashboard(@PathVariable final String scenarioId) {
    return ResponseEntity.ok(
        this.customDashboardService.findCustomDashboardByResourceId(scenarioId));
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/count/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public EsCountInterval dashboardCount(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardCountOnResourceId(scenarioId, widgetId, parameters);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/average/{widgetId}")
  @RBAC(
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

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/series/{widgetId}")
  @RBAC(
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

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/entities/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<EsBase> dashboardEntities(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardEntitiesOnResourceId(
        scenarioId, widgetId, parameters);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/entities-runtime/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      @PathVariable final String scenarioId,
      @PathVariable final String widgetId,
      @Valid @RequestBody WidgetToEntitiesInput input) {
    return this.customDashboardService.widgetToEntitiesRuntimeOnResourceId(
        scenarioId, widgetId, input);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/attack-paths/{widgetId}")
  @RBAC(
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
