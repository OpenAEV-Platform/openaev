package io.openaev.rest.dashboard;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.EsSearch;
import io.openaev.engine.query.EsAttackPath;
import io.openaev.engine.query.EsCountInterval;
import io.openaev.engine.query.EsSeries;
import io.openaev.rest.dashboard.model.WidgetToEntitiesInput;
import io.openaev.rest.dashboard.model.WidgetToEntitiesOutput;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DashboardApi extends RestBehavior {

  public static final String DASHBOARD_URI = "/api/dashboards";

  private final DashboardService dashboardService;

  @PostMapping(DASHBOARD_URI + "/count/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsCountInterval count(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.count(widgetId, parameters);
  }

  @PostMapping(DASHBOARD_URI + "/series/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsSeries> series(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.series(widgetId, parameters);
  }

  @PostMapping(DASHBOARD_URI + "/entities/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsBase> entities(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.entities(widgetId, parameters);
  }

  @PostMapping(DASHBOARD_URI + "/entities-runtime/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      @PathVariable final String widgetId, @Valid @RequestBody WidgetToEntitiesInput input) {
    return this.dashboardService.widgetToEntitiesRuntime(widgetId, input);
  }

  @PostMapping(DASHBOARD_URI + "/attack-paths/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsAttackPath> attackPaths(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return this.dashboardService.attackPaths(widgetId, parameters);
  }

  @GetMapping(DASHBOARD_URI + "/search/{search}")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public List<EsSearch> search(@PathVariable final String search) {
    return this.dashboardService.search(search);
  }
}
