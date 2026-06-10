package io.openaev.rest.dashboard;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
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

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/count/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsCountInterval count(
          TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.count(ctx, widgetId, parameters);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/average/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsAvgs average(TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.average(ctx, widgetId, parameters);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/series/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsSeries> series(TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.series(ctx, widgetId, parameters);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/entities/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsEntities entities(TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) EntitiesPaginationInput input) {
    return this.dashboardService.entities(ctx,
        widgetId,
        input == null ? new HashMap<>() : input.getParameters(),
        input == null ? null : input.getPagination());
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/entities-runtime/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(TxCtx ctx,
      @PathVariable final String widgetId, @Valid @RequestBody WidgetToEntitiesInput input) {
    return this.dashboardService.widgetToEntitiesRuntime(ctx, widgetId, input);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/attack-paths/{widgetId}")
  @AccessControl(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsAttackPath> attackPaths(
          TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return this.dashboardService.attackPaths(ctx, widgetId, parameters);
  }

  @GetMapping("/search/{search}")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  @Transactional(readOnly = true)
  public List<EsSearch> search(TxCtx ctx, @PathVariable final String search) {
    return this.dashboardService.search(ctx, search);
  }
}
