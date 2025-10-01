package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.ResourceType;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.query.EsAttackPath;
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
public class ExerciseDashboardApi {

  private final CustomDashboardService customDashboardService;

  @GetMapping(EXERCISE_URI + "/{simulationId}/dashboard")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Find the dashboard linked to a Simulation")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The dashboard"),
        @ApiResponse(responseCode = "404", description = "The Simulation doesn't exist")
      })
  public ResponseEntity<CustomDashboard> dashboard(@PathVariable final String simulationId) {
    return ResponseEntity.ok(
        this.customDashboardService.findCustomDashboardByResourceId(simulationId));
  }

  @PostMapping(EXERCISE_URI + "/{simulationId}/dashboard/count/{widgetId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public EsCountInterval dashboardCount(
      @PathVariable final String simulationId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardCountOnResourceId(
        simulationId, widgetId, parameters);
  }

  @PostMapping(EXERCISE_URI + "/{simulationId}/dashboard/series/{widgetId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<EsSeries> dashboardSeries(
      @PathVariable final String simulationId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardSeriesOnResourceId(
        simulationId, widgetId, parameters);
  }

  @PostMapping(EXERCISE_URI + "/{simulationId}/dashboard/entities/{widgetId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<EsBase> dashboardEntities(
      @PathVariable final String simulationId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardEntitiesOnResourceId(
        simulationId, widgetId, parameters);
  }

  @PostMapping(EXERCISE_URI + "/{simulationId}/dashboard/entities-runtime/{widgetId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      @PathVariable final String simulationId,
      @PathVariable final String widgetId,
      @Valid @RequestBody WidgetToEntitiesInput input) {
    return this.customDashboardService.widgetToEntitiesRuntimeOnResourceId(
        simulationId, widgetId, input);
  }

  @PostMapping(EXERCISE_URI + "/{simulationId}/dashboard/attack-paths/{widgetId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<EsAttackPath> dashboardAttackPaths(
      @PathVariable final String simulationId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return this.customDashboardService.dashboardAttackPathsOnResourceId(
        simulationId, widgetId, parameters);
  }
}
