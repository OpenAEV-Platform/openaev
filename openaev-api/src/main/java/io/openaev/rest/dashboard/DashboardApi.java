package io.openaev.rest.dashboard;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.engine.model.EsSearch;
import io.openaev.engine.query.*;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.es.EntitiesPaginationInput;
import io.openaev.utils.es.WidgetToEntitiesInput;
import io.openaev.utils.es.WidgetToEntitiesOutput;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({DashboardApi.DASHBOARD_URI, DashboardApi.TENANT_DASHBOARD_URI})
public class DashboardApi extends RestBehavior {

  public static final String DASHBOARD_URI = "/api/dashboards";
  public static final String TENANT_DASHBOARD_URI = TENANT_PREFIX + "/dashboards";

  private final DashboardService dashboardService;

  @PostMapping("/count/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsCountInterval count(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.count(widgetId, parameters);
  }

  @PostMapping("/average/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsAvgs average(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.average(widgetId, parameters);
  }

  @PostMapping("/series/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsSeries> series(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.series(widgetId, parameters);
  }

  @PostMapping("/entities/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsEntities entities(
      @PathVariable final String widgetId,
      @RequestBody(required = false) EntitiesPaginationInput input) {
    return this.dashboardService.entities(
        widgetId,
        input == null ? new HashMap<>() : input.getParameters(),
        input == null ? null : input.getPagination());
  }

  @PostMapping("/entities-runtime/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      @PathVariable final String widgetId, @Valid @RequestBody WidgetToEntitiesInput input) {
    return this.dashboardService.widgetToEntitiesRuntime(widgetId, input);
  }

  @PostMapping("/attack-paths/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsAttackPath> attackPaths(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return this.dashboardService.attackPaths(widgetId, parameters);
  }

  @GetMapping("/search/{search}")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public List<EsSearch> search(@PathVariable final String search) {
    return this.dashboardService.search(search);
  }
}
