package io.openaev.rest.custom_dashboard;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.*;
import static io.openaev.database.model.TenantSettingKeys.TENANT_HOME_DASHBOARD;
import static io.openaev.database.specification.CustomDashboardSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TxCtx;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.model.Widget;
import io.openaev.database.raw.RawCustomDashboard;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.engine.query.*;
import io.openaev.rest.custom_dashboard.form.CustomDashboardOutput;
import io.openaev.rest.custom_dashboard.form.CustomDashboardUsageOutput;
import io.openaev.rest.custom_dashboard.form.StarterPackDashboardsRestoreOutput;
import io.openaev.rest.dashboard.DashboardService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.ZipJsonService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.es.EntitiesPaginationInput;
import io.openaev.utils.es.WidgetToEntitiesInput;
import io.openaev.utils.es.WidgetToEntitiesOutput;
import io.openaev.utils.mapper.CustomDashboardMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomDashboardService {

  /** The dashboards the starter pack installs, bundled in the API jar. */
  public static final String STARTER_PACK_DASHBOARDS_PATTERN =
      "classpath:starterpack/dashboards/*.zip";

  /**
   * A bundled dashboard whose file name starts with one of these prefixes is the tenant default for
   * the setting it maps to (same convention as the starter pack import).
   */
  private static final Map<String, TenantSettingKeys> STARTER_PACK_DEFAULT_PREFIXES =
      Map.of(
          "default_home", TenantSettingKeys.TENANT_HOME_DASHBOARD,
          "default_scenario", TenantSettingKeys.TENANT_SCENARIO_DASHBOARD,
          "default_simulation", TenantSettingKeys.TENANT_SIMULATION_DASHBOARD);

  private static final String DASHBOARD_NAME_ATTRIBUTE = "custom_dashboard_name";

  private final CustomDashboardRepository customDashboardRepository;
  private final CustomDashboardMapper customDashboardMapper;
  private final TenantSettingsService tenantSettingsService;
  private final DashboardService dashboardService;
  private final ScenarioRepository scenarioRepository;
  private final ExerciseRepository exerciseRepository;
  private final ZipJsonService<CustomDashboard> zipJsonService;
  private final ResourcePatternResolver resourcePatternResolver;

  // -- CRUD --

  /**
   * Creates a new {@link CustomDashboard} entity in the database.
   *
   * @param customDashboard the {@link CustomDashboard} entity to save
   * @return the saved {@link CustomDashboard}
   */
  @Transactional
  public CustomDashboard createCustomDashboard(
      @NotNull final CustomDashboard customDashboard, @NotBlank final String tenantId) {
    return this.customDashboardRepository.save(prepareForTenantWrite(customDashboard, tenantId));
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

  public static CustomDashboard prepareForTenantWrite(
      @NotNull final CustomDashboard customDashboard, @NotBlank final String tenantId) {
    CustomDashboard prepared = sanityCheck(customDashboard);
    Tenant tenant = new Tenant(tenantId);
    prepared.setTenant(tenant);
    prepared.getWidgets().forEach(widget -> widget.setTenant(tenant));
    return prepared;
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
   * SearchPaginationInput}, with their widgets loaded inside the scoped transaction (see {@link
   * CustomDashboardRepository#findAllByIdInFetchingWidgets}).
   *
   * @param searchPaginationInput the pagination and filtering input
   * @return a {@link Page} of {@link CustomDashboard} entities
   */
  @Transactional(readOnly = true)
  public Page<CustomDashboard> customDashboards(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    Page<CustomDashboard> customDashboards =
        buildPaginationJPA(
            this.customDashboardRepository::findAll, searchPaginationInput, CustomDashboard.class);
    if (customDashboards.hasContent()) {
      this.customDashboardRepository.findAllByIdInFetchingWidgets(
          customDashboards.getContent().stream().map(CustomDashboard::getId).toList());
    }
    return customDashboards;
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
    return withWidgetsInitialized(
        this.customDashboardRepository
            .findById(id)
            .orElseThrow(
                () -> new EntityNotFoundException("Custom dashboard not found with id: " + id)));
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
  public void deleteCustomDashboard(@NotBlank final String tenantId, @NotNull final String id) {
    // Prevent deletion if used as home dashboard
    String defaultHomeDashboardId =
        this.tenantSettingsService
            .findSetting(tenantId, TENANT_HOME_DASHBOARD.key())
            .map(Setting::getValue)
            .orElse(null);
    if (defaultHomeDashboardId != null && defaultHomeDashboardId.equals(id)) {
      throw new BadRequestException("Default home custom dashboard can not be deleted");
    }
    // Clear scenario/simulation defaults if they reference this dashboard
    this.tenantSettingsService.clearSettingIfMatch(
        tenantId, TenantSettingKeys.TENANT_SCENARIO_DASHBOARD.key(), id);
    this.tenantSettingsService.clearSettingIfMatch(
        tenantId, TenantSettingKeys.TENANT_SIMULATION_DASHBOARD.key(), id);

    if (!this.customDashboardRepository.existsById(id)) {
      throw new EntityNotFoundException("Custom dashboard not found with id: " + id);
    }
    this.customDashboardRepository.deleteByIdNative(id);
  }

  // -- USAGE --

  /**
   * What deleting this dashboard would change: the scenarios and simulations that show it in their
   * Analysis tab, and the tenant default settings that name it. The delete is not refused for these
   * (the references are cleared), so clients read this first and ask for confirmation.
   *
   * @param id the dashboard id
   * @throws EntityNotFoundException if no dashboard is found with the given ID
   */
  @Transactional(readOnly = true)
  public CustomDashboardUsageOutput customDashboardUsage(@NotNull final String id) {
    CustomDashboard customDashboard =
        this.customDashboardRepository
            .findById(id)
            .orElseThrow(
                () -> new EntityNotFoundException("Custom dashboard not found with id: " + id));
    String tenantId = customDashboard.getTenant().getId();
    return new CustomDashboardUsageOutput(
        id,
        this.customDashboardRepository.countScenariosUsing(id),
        this.customDashboardRepository.countSimulationsUsing(id),
        isTenantSetting(tenantId, TENANT_HOME_DASHBOARD, id),
        isTenantSetting(tenantId, TenantSettingKeys.TENANT_SCENARIO_DASHBOARD, id),
        isTenantSetting(tenantId, TenantSettingKeys.TENANT_SIMULATION_DASHBOARD, id));
  }

  private boolean isTenantSetting(
      final String tenantId, final TenantSettingKeys key, final String dashboardId) {
    return this.tenantSettingsService
        .findSetting(tenantId, key.key())
        .map(Setting::getValue)
        .filter(dashboardId::equals)
        .isPresent();
  }

  // -- STARTER PACK RESTORE --

  /**
   * Re-creates the starter-pack dashboards the tenant no longer has, from the archives bundled in
   * the API, under their original names, and sets the tenant default home, scenario and simulation
   * dashboards back when they are empty. Idempotent: a dashboard whose name still exists is kept as
   * it is, and a default that holds a value is never overwritten.
   *
   * @param tenantId the tenant to restore the dashboards for
   * @return the dashboards created, the ones that already existed and the defaults set back
   */
  @Transactional
  public StarterPackDashboardsRestoreOutput restoreStarterPackDashboards(
      @NotBlank final String tenantId) {
    List<String> restored = new ArrayList<>();
    List<String> existing = new ArrayList<>();
    List<String> defaultsSet = new ArrayList<>();
    for (Resource resource : starterPackDashboardResources()) {
      String filename = Optional.ofNullable(resource.getFilename()).orElse("");
      try {
        byte[] bytes = resource.getContentAsByteArray();
        Object name = this.zipJsonService.readRootAttribute(bytes, DASHBOARD_NAME_ATTRIBUTE);
        if (!(name instanceof String dashboardName) || dashboardName.isBlank()) {
          log.warn("Starter pack dashboard file without a name, skipped: {}", filename);
          continue;
        }
        Optional<CustomDashboard> present =
            this.customDashboardRepository.findFirstByNameAndTenantIdOrderByCreationDateAsc(
                dashboardName, tenantId);
        String dashboardId;
        if (present.isPresent()) {
          dashboardId = present.get().getId();
          existing.add(dashboardName);
        } else {
          dashboardId =
              this.zipJsonService
                  .handleImport(
                      bytes,
                      DASHBOARD_NAME_ATTRIBUTE,
                      null,
                      customDashboard -> prepareForTenantWrite(customDashboard, tenantId),
                      "",
                      tenantId)
                  .persistedData()
                  .getId();
          restored.add(dashboardName);
        }
        TenantSettingKeys defaultKey = starterPackDefaultKey(filename);
        if (defaultKey != null
            && this.tenantSettingsService.setSettingIfBlank(
                tenantId, defaultKey.key(), dashboardId)) {
          defaultsSet.add(defaultKey.key());
        }
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read starter pack dashboard " + filename, e);
      }
    }
    return new StarterPackDashboardsRestoreOutput(restored, existing, defaultsSet);
  }

  private List<Resource> starterPackDashboardResources() {
    try {
      return Arrays.stream(
              this.resourcePatternResolver.getResources(STARTER_PACK_DASHBOARDS_PATTERN))
          .sorted(Comparator.comparing(r -> Optional.ofNullable(r.getFilename()).orElse("")))
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to list the starter pack dashboards", e);
    }
  }

  static TenantSettingKeys starterPackDefaultKey(final String filename) {
    return STARTER_PACK_DEFAULT_PREFIXES.entrySet().stream()
        .filter(entry -> filename.startsWith(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
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
    // A null / empty id list would make findAllById(null) throw "Ids must not be null".
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
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
   * Return the dashboard associated to a resource (scenario or simulation). When the resource has
   * no dashboard explicitly attached, fall back to the tenant default scenario / simulation
   * dashboard configured in the settings, so the Statistics tab always shows the default dashboard
   * out of the box.
   *
   * @param ctx the request scope the simulation or scenario is resolved in
   * @param resourceId simulation id or scenario id
   * @return the effective dashboard for this resource
   */
  // Explicitly transactional so the Hibernate tenant filter applies to the scenario / simulation
  // existence checks of the fallback, whatever the caller's context.
  @Transactional(readOnly = true)
  public CustomDashboard findCustomDashboardByResourceId(
      final TxCtx ctx, @NotBlank final String resourceId) {
    return withWidgetsInitialized(resolveCustomDashboardByResourceId(ctx, resourceId));
  }

  // Shared by the transactional public entry point above and the internal callers of this class
  // (which run inside their caller's transaction): intra-class calls must not target the
  // @Transactional method directly, the proxy would be bypassed.
  private CustomDashboard resolveCustomDashboardByResourceId(
      final TxCtx ctx, final String resourceId) {
    return customDashboardRepository
        .findByResourceId(resourceId)
        .or(() -> findTenantDefaultDashboardForResource(ctx, resourceId))
        .orElseThrow(
            () ->
                new EntityNotFoundException(
                    "Custom dashboard associated to resource: " + resourceId + " not found"));
  }

  private Optional<CustomDashboard> findTenantDefaultDashboardForResource(
      final TxCtx ctx, final String resourceId) {
    Set<String> scope = TxCtxScopeUtils.requestReadScope(ctx);
    Optional<Map.Entry<String, TenantSettingKeys>> scopedDashboardSetting =
        this.scenarioRepository
            .findById(resourceId)
            .filter(scenario -> scope.contains(scenario.getTenant().getId()))
            .map(
                scenario ->
                    Map.entry(
                        scenario.getTenant().getId(), TenantSettingKeys.TENANT_SCENARIO_DASHBOARD))
            .or(
                () ->
                    this.exerciseRepository
                        .findById(resourceId)
                        .filter(exercise -> scope.contains(exercise.getTenant().getId()))
                        .map(
                            exercise ->
                                Map.entry(
                                    exercise.getTenant().getId(),
                                    TenantSettingKeys.TENANT_SIMULATION_DASHBOARD)));
    return scopedDashboardSetting
        .flatMap(
            tenantAndKey ->
                this.tenantSettingsService.findSetting(
                    tenantAndKey.getKey(), tenantAndKey.getValue().key()))
        .map(Setting::getValue)
        .filter(value -> !value.isEmpty())
        .flatMap(this.customDashboardRepository::findById);
  }

  /**
   * Verify if a widget is part of a dashboard associated to a resource
   *
   * @param ctx the request scope the simulation or scenario is resolved in
   * @param resourceId simulation id or scenario id that is associated to a dashboard
   * @param widgetId
   * @return
   */
  public boolean isWidgetInResourceDashboard(
      final TxCtx ctx, @NotBlank final String resourceId, @NotBlank final String widgetId) {
    if (resolveCustomDashboardByResourceId(ctx, resourceId).getWidgets().stream()
        .map(Widget::getId)
        .collect(Collectors.toSet())
        .contains(widgetId)) {
      return true;
    }
    return false;
  }

  public EsCountInterval dashboardCountOnResourceId(
      TxCtx ctx,
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(ctx, resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.count(ctx, widgetId, parameters);
  }

  public EsAvgs dashboardAverageOnResourceId(
      TxCtx ctx,
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(ctx, resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.average(ctx, widgetId, parameters);
  }

  public List<EsSeries> dashboardSeriesOnResourceId(
      TxCtx ctx,
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(ctx, resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.series(ctx, widgetId, parameters);
  }

  public EsEntities dashboardEntitiesOnResourceId(
      TxCtx ctx,
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      @Nullable final EntitiesPaginationInput input) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(ctx, resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.entities(
        ctx,
        widgetId,
        input == null ? new HashMap<>() : input.getParameters(),
        input == null ? null : input.getPagination());
  }

  public WidgetToEntitiesOutput widgetToEntitiesRuntimeOnResourceId(
      TxCtx ctx,
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      @NotBlank WidgetToEntitiesInput input) {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(ctx, resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.widgetToEntitiesRuntime(ctx, widgetId, input);
  }

  public List<EsAttackPath> dashboardAttackPathsOnResourceId(
      TxCtx ctx,
      @NotBlank final String resourceId,
      @NotBlank final String widgetId,
      final Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    // verify that the widget is in the resource dashboard
    if (!isWidgetInResourceDashboard(ctx, resourceId, widgetId)) {
      throw new AccessDeniedException("Access denied");
    }
    return this.dashboardService.attackPaths(ctx, widgetId, parameters);
  }

  private CustomDashboard withWidgetsInitialized(@NotNull CustomDashboard customDashboard) {
    Hibernate.initialize(customDashboard.getWidgets());
    return customDashboard;
  }
}
