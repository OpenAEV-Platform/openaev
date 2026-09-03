package io.openaev.rest.dashboard;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.dashboard.dto.AdHocWidgetInput;
import io.openaev.api.dashboard.dto.AdHocWidgetToEntitiesInput;
import io.openaev.context.TxCtx;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({DashboardApi.DASHBOARD_URI, DashboardApi.TENANT_DASHBOARD_URI})
public class DashboardApi extends RestBehavior {

  public static final String DASHBOARD_URI = "/api/dashboards";
  public static final String TENANT_DASHBOARD_URI = TENANT_PREFIX + "/dashboards";

  private final DashboardService dashboardService;

  @PostMapping("/count/{widgetId}")
  @Transactional
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
  @Transactional
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
  @Transactional
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
  @Transactional
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
  @Transactional
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      @PathVariable final String widgetId, @Valid @RequestBody WidgetToEntitiesInput input) {
    return this.dashboardService.widgetToEntitiesRuntime(widgetId, input);
  }

  @PostMapping("/attack-paths/{widgetId}")
  @Transactional
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
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public List<EsSearch> search(TxCtx ctx, @PathVariable final String search) {
    return this.dashboardService.search(search);
  }

  // -- AD-HOC (non-persisted) WIDGET QUERIES --
  // Used by the built-in platform default home dashboard: the caller provides
  // the full widget configuration instead of referencing a stored widget.
  // Same access level as the tenant home-dashboard widget endpoints; the data
  // itself is scoped by the current user grants inside the engine.

  @PostMapping("/adhoc/series")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  public List<EsSeries> adHocSeries(TxCtx ctx, @Valid @RequestBody AdHocWidgetInput input) {
    return this.dashboardService.adHocSeries(input.getWidgetConfiguration(), input.getParameters());
  }

  @PostMapping("/adhoc/count")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  public EsCountInterval adHocCount(TxCtx ctx, @Valid @RequestBody AdHocWidgetInput input) {
    return this.dashboardService.adHocCount(input.getWidgetConfiguration(), input.getParameters());
  }

  @PostMapping("/adhoc/average")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  public EsAvgs adHocAverage(TxCtx ctx, @Valid @RequestBody AdHocWidgetInput input) {
    return this.dashboardService.adHocAverage(
        input.getWidgetConfiguration(), input.getParameters());
  }

  @PostMapping("/adhoc/entities")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  public EsEntities adHocEntities(TxCtx ctx, @Valid @RequestBody AdHocWidgetInput input) {
    return this.dashboardService.adHocEntities(
        input.getWidgetConfiguration(), input.getParameters(), input.getPagination());
  }

  @PostMapping("/adhoc/entities-runtime")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  public WidgetToEntitiesOutput adHocEntitiesRuntime(
      TxCtx ctx, @Valid @RequestBody AdHocWidgetToEntitiesInput input) {
    return this.dashboardService.adHocEntitiesRuntime(
        input.getWidgetType(), input.getWidgetConfiguration(), input);
  }
}
