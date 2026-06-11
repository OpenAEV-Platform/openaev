package io.openaev.rest.custom_dashboard;

import io.openaev.context.TxCtx;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.engine.query.*;
import io.openaev.rest.dashboard.DashboardService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.es.EntitiesPaginationInput;
import io.openaev.utils.es.WidgetToEntitiesInput;
import io.openaev.utils.es.WidgetToEntitiesOutput;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomDashboardTenantService {

  private final CustomDashboardRepository customDashboardRepository;
  private final TenantSettingsService tenantSettingsService;
  private final DashboardService dashboardService;

  // -- READ --

  /**
   * Finds the home dashboard for the given tenant by resolving the dashboard ID from tenant
   * settings.
   */
  public Optional<CustomDashboard> findTenantHomeDashboard(@NotBlank String tenantId) {
    return tenantSettingsService
        .findHomeDashboardId(tenantId)
        .flatMap(customDashboardRepository::findById);
  }

  // -- HOME DASHBOARD WIDGET QUERIES --

  public EsCountInterval homeDashboardCount(
      TxCtx ctx,
      @NotBlank String tenantId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    isWidgetInHomeDashboard(tenantId, widgetId);
    return dashboardService.count(ctx, widgetId, parameters);
  }

  public EsAvgs homeDashboardAverage(
      TxCtx ctx, @NotBlank final String widgetId, final Map<String, String> parameters) {
    isWidgetInHomeDashboard(ctx.tenantIdFromUri(), widgetId);
    return dashboardService.average(ctx, widgetId, parameters);
  }

  public List<EsSeries> homeDashboardSeries(
      TxCtx ctx, @NotBlank final String widgetId, final Map<String, String> parameters) {
    isWidgetInHomeDashboard(ctx.tenantIdFromUri(), widgetId);
    return dashboardService.series(ctx, widgetId, parameters);
  }

  public EsEntities homeDashboardEntities(
      TxCtx ctx, @NotBlank final String widgetId, @Nullable final EntitiesPaginationInput input) {
    isWidgetInHomeDashboard(ctx.tenantIdFromUri(), widgetId);
    return dashboardService.entities(
        ctx,
        widgetId,
        input == null ? new HashMap<>() : input.getParameters(),
        input == null ? null : input.getPagination());
  }

  public WidgetToEntitiesOutput homeDashboardEntitiesRuntime(
      TxCtx ctx, @NotBlank final String widgetId, @NotBlank WidgetToEntitiesInput input) {
    isWidgetInHomeDashboard(ctx.tenantIdFromUri(), widgetId);
    return dashboardService.widgetToEntitiesRuntime(ctx, widgetId, input);
  }

  public List<EsAttackPath> homeDashboardAttackPaths(
      TxCtx ctx, @NotBlank final String widgetId, final Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    isWidgetInHomeDashboard(ctx.tenantIdFromUri(), widgetId);
    return dashboardService.attackPaths(ctx, widgetId, parameters);
  }

  // -- PRIVATE HELPERS --

  /** Verifies that the given widget belongs to the tenant home dashboard. */
  private void isWidgetInHomeDashboard(String tenantId, String widgetId) {
    boolean found =
        findTenantHomeDashboard(tenantId)
            .map(d -> d.getWidgets().stream().anyMatch(w -> widgetId.equals(w.getId())))
            .orElse(false);
    if (!found) {
      throw new AccessDeniedException("Access denied");
    }
  }
}
