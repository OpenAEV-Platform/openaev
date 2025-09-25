package io.openbas.rest.scenario;

import static io.openbas.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.CustomDashboard;
import io.openbas.database.model.ResourceType;
import io.openbas.engine.model.EsBase;
import io.openbas.engine.query.EsAttackPath;
import io.openbas.engine.query.EsCountInterval;
import io.openbas.engine.query.EsSeries;
import io.openbas.rest.custom_dashboard.CustomDashboardService;
import io.openbas.rest.dashboard.model.WidgetToEntitiesInput;
import io.openbas.rest.dashboard.model.WidgetToEntitiesOutput;
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
