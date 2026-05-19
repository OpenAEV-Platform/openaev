package io.openaev.rest.custom_dashboard;

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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CustomDashboardTenantService {

  private final CustomDashboardRepository customDashboardRepository;
  private final TenantSettingsService tenantSettingsService;
  private final DashboardService dashboardService;

  // -- READ --

  /**
   * Finds the home dashboard for the given tenant by resolving the dashboard ID from tenant
   * settings.
   */
  @Transactional(readOnly = true)
  public Optional<CustomDashboard> findTenantHomeDashboard(@NotBlank String tenantId) {
    return tenantSettingsService
        .findHomeDashboardId(tenantId)
        .flatMap(customDashboardRepository::findById);
  }

  // -- HOME DASHBOARD WIDGET QUERIES --

  @Transactional(readOnly = true)
  public EsCountInterval homeDashboardCount(
      @NotBlank String tenantId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    isWidgetInHomeDashboard(tenantId, widgetId);
    return dashboardService.count(widgetId, parameters);
  }

  @Transactional(readOnly = true)
  public EsAvgs homeDashboardAverage(
      @NotBlank String tenantId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    isWidgetInHomeDashboard(tenantId, widgetId);
    return dashboardService.average(widgetId, parameters);
  }

  @Transactional(readOnly = true)
  public List<EsSeries> homeDashboardSeries(
      @NotBlank String tenantId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    isWidgetInHomeDashboard(tenantId, widgetId);
    return dashboardService.series(widgetId, parameters);
  }

  @Transactional(readOnly = true)
  public EsEntities homeDashboardEntities(
      @NotBlank String tenantId,
      @NotBlank final String widgetId,
      @Nullable final EntitiesPaginationInput input) {
    isWidgetInHomeDashboard(tenantId, widgetId);
    return dashboardService.entities(
        widgetId,
        input == null ? new HashMap<>() : input.getParameters(),
        input == null ? null : input.getPagination());
  }

  @Transactional(readOnly = true)
  public WidgetToEntitiesOutput homeDashboardEntitiesRuntime(
      @NotBlank String tenantId,
      @NotBlank final String widgetId,
      @NotBlank WidgetToEntitiesInput input) {
    isWidgetInHomeDashboard(tenantId, widgetId);
    return dashboardService.widgetToEntitiesRuntime(widgetId, input);
  }

  @Transactional(readOnly = true)
  public List<EsAttackPath> homeDashboardAttackPaths(
      @NotBlank String tenantId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    isWidgetInHomeDashboard(tenantId, widgetId);
    return dashboardService.attackPaths(widgetId, parameters);
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
