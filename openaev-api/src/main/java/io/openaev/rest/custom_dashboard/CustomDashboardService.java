package io.openaev.rest.custom_dashboard;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.*;
import static io.openaev.database.specification.CustomDashboardSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.Widget;
import io.openaev.database.raw.RawCustomDashboard;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.query.EsAttackPath;
import io.openaev.engine.query.EsCountInterval;
import io.openaev.engine.query.EsSeries;
import io.openaev.rest.custom_dashboard.form.CustomDashboardOutput;
import io.openaev.rest.dashboard.DashboardService;
import io.openaev.rest.dashboard.model.WidgetToEntitiesInput;
import io.openaev.rest.dashboard.model.WidgetToEntitiesOutput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.mapper.CustomDashboardMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomDashboardService {

  private final CustomDashboardRepository customDashboardRepository;
  private final CustomDashboardMapper customDashboardMapper;
  private final PlatformSettingsService platformSettingsService;
  private final DashboardService dashboardService;

  // -- CRUD --

  /**
   * Creates a new {@link CustomDashboard} entity in the database.
   *
   * @param customDashboard the {@link CustomDashboard} entity to save
   * @return the saved {@link CustomDashboard}
   */
  @Transactional
  public CustomDashboard createCustomDashboard(@NotNull final CustomDashboard customDashboard) {
    CustomDashboard customDashboardWithDefaultParams = initParameters(customDashboard);
    return this.customDashboardRepository.save(customDashboardWithDefaultParams);
  }

  public static CustomDashboard sanityCheck(@NotNull final CustomDashboard customDashboard) {
    if (customDashboard.getParameters() == null) {
      return initParameters(customDashboard);
    }
    CustomDashboard customDashboardWithDefaultParams = customDashboard;
    if (customDashboard.getParameters().stream().noneMatch(p -> p.getType().equals(timeRange))) {
      customDashboardWithDefaultParams = customDashboard.addParameter("Time range", timeRange);
    }
    if (customDashboard.getParameters().stream().noneMatch(p -> p.getType().equals(startDate))) {
      customDashboardWithDefaultParams = customDashboard.addParameter("Start date", startDate);
    }
    if (customDashboard.getParameters().stream().noneMatch(p -> p.getType().equals(endDate))) {
      customDashboardWithDefaultParams = customDashboard.addParameter("End date", endDate);
    }
    return customDashboardWithDefaultParams;
  }

  private static CustomDashboard initParameters(@NotNull final CustomDashboard customDashboard) {
    return customDashboard
        .addParameter("Time range", timeRange)
        .addParameter("Start date", startDate)
        .addParameter("End date", endDate);
  }

  /**
   * Retrieves all {@link CustomDashboard} entities from the database and converts them into {@link
   * CustomDashboardOutput} DTOs.
   *
   * @return list of {@link CustomDashboardOutput} DTOs
   */
  @Transactional(readOnly = true)
  public List<CustomDashboardOutput> customDashboards() {
    List<RawCustomDashboard> customDashboards = customDashboardRepository.rawAll();
    return customDashboardMapper.getCustomDashboardOutputs(customDashboards);
  }

  /**
   * Retrieves a paginated list of {@link CustomDashboard} entities according to the provided {@link
   * SearchPaginationInput}.
   *
   * @param searchPaginationInput the pagination and filtering input
   * @return a {@link Page} of {@link CustomDashboard} entities
   */
  @Transactional(readOnly = true)
  public Page<CustomDashboard> customDashboards(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        this.customDashboardRepository::findAll, searchPaginationInput, CustomDashboard.class);
  }

  /**
   * Retrieves a single {@link CustomDashboard} entity by its ID.
   *
   * @param id the unique ID of the custom dashboard
   * @return the {@link CustomDashboard} entity
   * @throws EntityNotFoundException if no dashboard is found with the given ID
   */
  @Transactional(readOnly = true)
  public CustomDashboard customDashboard(@NotNull final String id) {
    return this.customDashboardRepository
        .findById(id)
        .orElseThrow(
            () -> new EntityNotFoundException("Custom dashboard not found with id: " + id));
  }

  /**
   * Updates an existing {@link CustomDashboard} entity. The update date is set to the current
   * timestamp.
   *
   * @param customDashboard the {@link CustomDashboard} entity to update
   * @return the updated {@link CustomDashboard}
   */
  @Transactional
  public CustomDashboard updateCustomDashboard(@NotNull final CustomDashboard customDashboard) {
    customDashboard.setUpdateDate(Instant.now());
    return this.customDashboardRepository.save(customDashboard);
  }

  /**
   * Deletes a {@link CustomDashboard} entity by its ID.
   *
   * @param id the unique ID of the dashboard to delete
   * @throws EntityNotFoundException if no dashboard is found with the given ID or if it is set as
   *     the default home dashboard
   */
  @Transactional
  public void deleteCustomDashboard(@NotNull final String id) {
    String defaultHomeDashboardId =
        this.platformSettingsService
            .setting(SettingKeys.DEFAULT_HOME_DASHBOARD.key())
            .map(Setting::getValue)
            .orElse(null);
    if (defaultHomeDashboardId != null && defaultHomeDashboardId.equals(id)) {
      throw new BadRequestException("Default home custom dashboard can not be deleted");
    }
    this.platformSettingsService.clearDefaultPlatformDashboardIfMatch(id);
    if (!this.customDashboardRepository.existsById(id)) {
      throw new EntityNotFoundException("Custom dashboard not found with id: " + id);
    }
    this.customDashboardRepository.deleteById(id);
  }

  // -- OPTION --

  /**
   * Finds all {@link CustomDashboard} entities matching a search text, and returns them as {@link
   * FilterUtilsJpa.Option} DTOs for use in UI dropdowns.
   *
   * @param searchText partial or full name to filter dashboards
   * @return list of {@link FilterUtilsJpa.Option} objects
   */
  public List<FilterUtilsJpa.Option> findAllAsOptions(final String searchText) {
    return fromIterable(
            customDashboardRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  /**
   * Finds all {@link CustomDashboard} entities whose IDs are in the given list, and returns them as
   * {@link FilterUtilsJpa.Option} DTOs for use in UI dropdowns.
   *
   * @param ids list of dashboard IDs
   * @return list of {@link FilterUtilsJpa.Option} objects
   */
  public List<FilterUtilsJpa.Option> findAllByIdsAsOptions(final List<String> ids) {
    return fromIterable(customDashboardRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  public List<FilterUtilsJpa.Option> findAllByResourceIdAsOptions(@NotBlank String resourceId) {
    Optional<CustomDashboard> customDashboard =
        customDashboardRepository.findByResourceId(resourceId);
    if (customDashboard.isPresent()) {
      CustomDashboard cd = customDashboard.get();
      return List.of(new FilterUtilsJpa.Option(cd.getId(), cd.getName()));
    } else {
      return List.of();
    }
  }

  /**
   * Return the dashboard associated to a resource (scenario or simulation)
   *
   * @param resourceId simulation id or scenario id
   * @return
   */
  public CustomDashboard findCustomDashboardByResourceId(@NotBlank final String resourceId) {
    return customDashboardRepository
        .findByResourceId(resourceId)
        .orElseThrow(
            () ->
                new EntityNotFoundException(
                    "Custom dashboard associated to resource: " + resourceId + " not found"));
  }

  public Optional<CustomDashboard> findHomeDashboard() {
    return customDashboardRepository.findHomeDashboard();
  }

  /**
   * Verify if a widget is part of a dashboard associated to a resource
   *
   * @param resourceId simulation id or scenario id that is associated to a dashboard
   * @param widgetId
   * @return
   */
  public boolean isWidgetInResourceDashboard(
      @NotBlank final String resourceId, @NotBlank final String widgetId) {
    if (findCustomDashboardByResourceId(resourceId).getWidgets().stream()
        .map(Widget::getId)
        .collect(Collectors.toSet())
        .contains(widgetId)) {
      return true;
    }
    return false;
  }

  /**
   * Verify if a widget is part of a the home dashboard
   *
   * @param widgetId
   * @return
   */
  public boolean isWidgetInHomeDashboard(@NotBlank final String widgetId) {
    return findHomeDashboard()
        .map(d -> d.getWidgets().stream().anyMatch(w -> widgetId.equals(w.getId())))
        .orElse(false);
  }

  public EsCountInterval dashboardCountOnResourceId(
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.count(widgetId, parameters);
  }

  public List<EsSeries> dashboardSeriesOnResourceId(
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.series(widgetId, parameters);
  }

  public List<EsBase> dashboardEntitiesOnResourceId(
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.entities(widgetId, parameters);
  }

  public WidgetToEntitiesOutput widgetToEntitiesRuntimeOnResourceId(
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      @NotBlank WidgetToEntitiesInput input) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.widgetToEntitiesRuntime(widgetId, input);
  }

  public List<EsAttackPath> dashboardAttackPathsOnResourceId(
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.attackPaths(widgetId, parameters);
  }

  public EsCountInterval homeDashboardCount(
      @NotBlank final String widgetId, final Map<String, String> parameters) {

    // verify that the widget is in the home  dashboard
    if (!isWidgetInHomeDashboard(widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return dashboardService.count(widgetId, parameters);
  }

  public List<EsSeries> homeDashboardSeries(
      @NotBlank final String widgetId, final Map<String, String> parameters) {
    // verify that the widget is in the home  dashboard
    if (!isWidgetInHomeDashboard(widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return dashboardService.series(widgetId, parameters);
  }

  public List<EsBase> homeDashboardEntities(
      @NotBlank final String widgetId, final Map<String, String> parameters) {
    // verify that the widget is in the home  dashboard
    if (!isWidgetInHomeDashboard(widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return dashboardService.entities(widgetId, parameters);
  }

  public WidgetToEntitiesOutput homeWidgetToEntitiesRuntimeOnResourceId(
      @NotBlank final String widgetId, @NotBlank WidgetToEntitiesInput input) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInHomeDashboard(widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.widgetToEntitiesRuntime(widgetId, input);
  }

  public List<EsAttackPath> homeDashboardAttackPaths(
      @NotBlank final String widgetId, final Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    // verify that the widget is in the home  dashboard
    if (!isWidgetInHomeDashboard(widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return dashboardService.attackPaths(widgetId, parameters);
  }
}
