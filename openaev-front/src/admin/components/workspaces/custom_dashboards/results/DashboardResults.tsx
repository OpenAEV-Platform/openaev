import { ArrowBackOutlined } from '@mui/icons-material';
import { IconButton, Tooltip, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { fetchCustomDashboard } from '../../../../../actions/custom_dashboards/customdashboard-action';
import { adHocEntitiesRuntime, widgetToEntitiesRuntime } from '../../../../../actions/dashboards/dashboard-action';
import { fetchCustomDashboardFromSimulation, widgetToEntitiesBySimulation } from '../../../../../actions/exercises/exercise-action';
import { fetchCustomDashboardFromScenario, widgetToEntitiesByByScenario } from '../../../../../actions/scenarios/scenario-actions';
import { fetchTenantHomeDashboard, tenantHomeWidgetToEntitiesRuntime } from '../../../../../actions/settings/tenant-settings-action';
import { buildFilter } from '../../../../../components/common/queryable/filter/FilterUtils';
import { DEFAULT_ROWS_PER_PAGE } from '../../../../../components/common/queryable/pagination/usePaginationState';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import {
  type CustomDashboard,
  type EsEntities,
  type Filter,
  type ListConfiguration,
  type WidgetToEntitiesInput,
  type WidgetToEntitiesOutput,
} from '../../../../../utils/api-types';
import { buildDefaultHomeWidgets, type DefaultTimeRange, timeRangeStartDate } from '../../../default_dashboard/defaultHomeWidgets';
import { buildContextualWidget } from './contextualWidgets';
import ResultsExplorer from './ResultsExplorer';

/**
 * Where the clicked widget lives. Each surface re-resolves the drill-down
 * through its own permission-scoped runtime endpoint:
 * - default:    the built-in hardcoded home dashboard (ad-hoc endpoint)
 * - tenant:     the tenant/user home dashboard
 * - workspace:  a custom dashboard opened from the Dashboards workspace
 * - simulation: the dashboard of a simulation's Statistics tab
 * - scenario:   the dashboard of a scenario's Statistics tab
 */
type ResultsSourceType = 'default' | 'tenant' | 'workspace' | 'simulation' | 'scenario';

const RESERVED_PARAMS = ['widget_id', 'series_index', 'source', 'context_id', 'dashboard_id', 'back'];
const PARAM_PREFIX = 'param.';

const NAMED_TIME_RANGES = new Set(['LAST_DAY', 'LAST_WEEK', 'LAST_MONTH', 'LAST_QUARTER', 'LAST_SEMESTER', 'LAST_YEAR']);

/**
 * Full-page drill-down for every dashboard surface: a click on a score, bar,
 * gate, gauge or list row lands here with the widget id, the clicked scope,
 * the dashboard source and a back link in the URL. The source-matching
 * runtime endpoint converts the widget into a scoped entity list once, then
 * the standard queryable machinery (filter chips, sorting, pagination)
 * takes over.
 */
const DashboardResults = () => {
  const { t, locale } = useFormatter();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const widgetId = searchParams.get('widget_id');
  const seriesIndex = Number(searchParams.get('series_index') ?? 0);
  const source = (searchParams.get('source') ?? 'default') as ResultsSourceType;
  const contextId = searchParams.get('context_id');
  // Internal path only: the back target always comes from our own navigation.
  const backParam = searchParams.get('back');
  const backUrl = backParam && backParam.startsWith('/') ? backParam : '/admin';

  // Dashboard parameter values travel prefixed (param.<id>) so they cannot
  // collide with clicked filter values.
  const parameters = useMemo(() => Object.fromEntries(
    [...new Set(searchParams.keys())]
      .filter(key => key.startsWith(PARAM_PREFIX))
      .map(key => [key.substring(PARAM_PREFIX.length), searchParams.get(key) ?? '']),
  ), [searchParams]);

  // Clicked scope values are carried as one URL param per value (repeatable keys).
  const filterValues = useMemo(() => Object.fromEntries(
    [...new Set(searchParams.keys())]
      .filter(key => !RESERVED_PARAMS.includes(key) && !key.startsWith(PARAM_PREFIX))
      .map(key => [key, searchParams.getAll(key).filter(value => value !== '')]),
  ), [searchParams]);

  // Same time range as the built-in home dashboard so the list matches what
  // was clicked (default source only).
  const [defaultTimeRange] = useLocalStorage<DefaultTimeRange>('default-home-dashboard-time-range', 'LAST_QUARTER');
  // `t` from useFormatter() is a NEW function on every render, so it must NOT be
  // a dependency here: it would give `defaultWidget` (and thus the seed effect) a
  // fresh identity every render, re-running the fetch on every render and looping
  // the ad-hoc endpoint. `locale` is the stable signal that t's output changed.
  const defaultWidget = useMemo(
    () => (source === 'default' ? buildDefaultHomeWidgets(defaultTimeRange, t).find(w => w.widget_id === widgetId) : undefined),
    [source, defaultTimeRange, widgetId, locale],
  );

  // Synthetic overview drill-downs (scenario / simulation MITRE matrix and
  // posture gauges): the widget is rebuilt client-side from the URL and
  // resolved through the ad-hoc endpoint, like the built-in home widgets.
  const contextualWidget = useMemo(
    () => buildContextualWidget(widgetId, source, contextId, t),
    [widgetId, source, contextId, locale],
  );

  // Stored dashboard sources: the definition is re-read through the surface's
  // own permission-scoped endpoint for the widget title, the time range and
  // the parameter definitions (needed to substitute parameter placeholders in
  // the seeded filters below).
  const needsDashboard = source !== 'default' && !contextualWidget;
  const [dashboard, setDashboard] = useState<CustomDashboard | null>(null);
  const [dashboardResolved, setDashboardResolved] = useState(false);
  useEffect(() => {
    if (!needsDashboard) {
      return undefined;
    }
    let cancelled = false;
    setDashboardResolved(false);
    const fetchDashboard = () => {
      switch (source) {
        case 'tenant':
          return fetchTenantHomeDashboard();
        case 'simulation':
          return fetchCustomDashboardFromSimulation(contextId ?? '');
        case 'scenario':
          return fetchCustomDashboardFromScenario(contextId ?? '');
        default:
          return fetchCustomDashboard(searchParams.get('dashboard_id') ?? '');
      }
    };
    fetchDashboard().then(({ data }: { data: CustomDashboard }) => {
      if (!cancelled) setDashboard(data);
    }).catch(() => {
      // Title/time-range/parameter fallback only: the runtime seed still resolves.
    }).finally(() => {
      if (!cancelled) setDashboardResolved(true);
    });
    return () => {
      cancelled = true;
    };
  }, [needsDashboard, source, contextId]);

  const storedWidget = useMemo(
    () => dashboard?.custom_dashboard_widgets?.find(w => w.widget_id === widgetId),
    [dashboard, widgetId],
  );
  const widget = contextualWidget ?? (source === 'default' ? defaultWidget : storedWidget);

  const [seed, setSeed] = useState<{
    listConfig: ListConfiguration;
    entities?: EsEntities;
  } | null>(null);
  const [seedError, setSeedError] = useState(false);

  // Widget series filters can reference dashboard parameters: their VALUES
  // are then the parameter ids, substituted with the selected values at
  // resolution time. The runtime endpoint does that substitution server-side
  // for the data but returns the RAW filters in the list configuration, so
  // the placeholders must be resolved here too - both for display (the chip
  // must read "Simulations = test", not an opaque parameter id) and for
  // correctness (the explorer re-queries with these filters as soon as they
  // are amended). Unselected parameters scope nothing: their values (and the
  // filter, when nothing else remains) are dropped, like the runtime does.
  const resolvedListConfig = useMemo((): ListConfiguration | null => {
    if (seed == null) {
      return null;
    }
    const filter = seed.listConfig.perspective?.filter;
    if (!filter) {
      return seed.listConfig;
    }
    const parameterIds = new Set(
      (dashboard?.custom_dashboard_parameters ?? [])
        .map(parameter => parameter.custom_dashboards_parameter_id)
        .filter((id): id is string => !!id),
    );
    const filters = (filter.filters ?? []).flatMap((f) => {
      const originalValues = f.values ?? [];
      const values = originalValues.flatMap((value) => {
        if (value in parameters) return [parameters[value]];
        if (parameterIds.has(value)) return [];
        return [value];
      });
      if (originalValues.length > 0 && values.length === 0) return [];
      return [{
        ...f,
        values,
      }];
    });
    return {
      ...seed.listConfig,
      perspective: {
        ...seed.listConfig.perspective,
        filter: {
          ...filter,
          filters,
        },
      },
    };
  }, [seed, dashboard, parameters]);

  // One runtime call resolves the clicked widget scope into a list
  // configuration (+ its first page); the explorer below owns everything else.
  useEffect(() => {
    if (!widgetId || (source === 'default' && !defaultWidget)) {
      return undefined;
    }
    let cancelled = false;
    setSeed(null);
    setSeedError(false);
    const input: WidgetToEntitiesInput = {
      filter_values_map: filterValues,
      series_index: seriesIndex,
      parameters,
      pagination: {
        page: 0,
        size: DEFAULT_ROWS_PER_PAGE,
      },
    };
    const seedCall = () => {
      if (contextualWidget) {
        return adHocEntitiesRuntime(contextualWidget.widget_type, contextualWidget.widget_config, input);
      }
      switch (source) {
        case 'tenant':
          return tenantHomeWidgetToEntitiesRuntime(widgetId, input);
        case 'workspace':
          return widgetToEntitiesRuntime(widgetId, input);
        case 'simulation':
          return widgetToEntitiesBySimulation(contextId ?? '', widgetId, input);
        case 'scenario':
          return widgetToEntitiesByByScenario(contextId ?? '', widgetId, input);
        default:
          return adHocEntitiesRuntime(defaultWidget!.widget_type, defaultWidget!.widget_config, input);
      }
    };
    seedCall().then(({ data }: { data: WidgetToEntitiesOutput }) => {
      if (cancelled) return;
      if (data.list_configuration == null) {
        setSeedError(true);
        return;
      }
      setSeed({
        listConfig: data.list_configuration,
        entities: data.es_entities,
      });
    }).catch(() => {
      if (!cancelled) setSeedError(true);
    });
    return () => {
      cancelled = true;
    };
  }, [source, contextId, widgetId, defaultWidget, contextualWidget, filterValues, seriesIndex, parameters]);

  // The widget scope is implicitly time-bounded (its time_range is applied
  // server-side when resolving the drill-down). Materialize that bound as
  // regular date filter chips so the list openly shows the dashboard time
  // range and lets the user widen or narrow it. Temporal bucket clicks come
  // back as a CUSTOM range (the clicked interval) and take precedence over
  // the dashboard-wide range.
  const seedDateFilters = useMemo((): Filter[] => {
    if (seed == null) {
      return [];
    }
    const dateAttribute = seed.listConfig.date_attribute ?? 'base_created_at';
    if (seed.listConfig.time_range === 'CUSTOM') {
      return [
        ...(seed.listConfig.start ? [buildFilter(dateAttribute, [seed.listConfig.start], 'gte')] : []),
        ...(seed.listConfig.end ? [buildFilter(dateAttribute, [seed.listConfig.end], 'lte')] : []),
      ];
    }
    if (source === 'default') {
      const start = timeRangeStartDate(defaultTimeRange);
      return start ? [buildFilter(dateAttribute, [start], 'gte')] : [];
    }
    // Stored widgets: the widget's own named range, else the dashboard
    // timeRange parameter carried in the URL (param.<id> where the parameter
    // is of type timeRange). CUSTOM dashboard ranges travel as startDate /
    // endDate parameters.
    let effectiveRange = seed.listConfig.time_range as string | undefined;
    if (!effectiveRange || effectiveRange === 'DEFAULT') {
      const timeRangeParamId = dashboard?.custom_dashboard_parameters
        ?.find(p => p.custom_dashboards_parameter_type === 'timeRange')
        ?.custom_dashboards_parameter_id;
      effectiveRange = timeRangeParamId ? parameters[timeRangeParamId] : undefined;
    }
    if (effectiveRange === 'CUSTOM') {
      const paramOfType = (type: string) => {
        const id = dashboard?.custom_dashboard_parameters
          ?.find(p => p.custom_dashboards_parameter_type === type)
          ?.custom_dashboards_parameter_id;
        return id ? parameters[id] : undefined;
      };
      const start = paramOfType('startDate');
      const end = paramOfType('endDate');
      return [
        ...(start ? [buildFilter(dateAttribute, [start], 'gte')] : []),
        ...(end ? [buildFilter(dateAttribute, [end], 'lte')] : []),
      ];
    }
    if (effectiveRange && NAMED_TIME_RANGES.has(effectiveRange)) {
      const start = timeRangeStartDate(effectiveRange as DefaultTimeRange);
      return start ? [buildFilter(dateAttribute, [start], 'gte')] : [];
    }
    return [];
  }, [seed, source, defaultTimeRange, dashboard, parameters]);

  const widgetTitle = widget?.widget_config.title || t('Results');

  if (!widgetId || (source === 'default' && !defaultWidget)) {
    return (
      <Typography variant="subtitle1" align="center" sx={{ marginTop: 6 }}>
        {t('No data to display')}
      </Typography>
    );
  }

  return (
    <>
      {/* Compact header: back to the originating dashboard + the clicked widget title */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 16,
        }}
      >
        <Tooltip title={t('Back')}>
          <IconButton onClick={() => navigate(backUrl)} aria-label={t('Back')} size="small">
            <ArrowBackOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Typography variant="h1" sx={{ margin: 0 }}>
          {widgetTitle}
        </Typography>
      </div>
      {seedError && (
        <Typography variant="subtitle1" align="center" sx={{ marginTop: 6 }}>
          {t('No data to display')}
        </Typography>
      )}
      {/* The explorer snapshots its filter state at mount: wait for the
          dashboard definition (parameter ids, time range) so the seeded
          chips are resolved before the first render. */}
      {!seedError && (resolvedListConfig == null || (needsDashboard && !dashboardResolved)) && <Loader variant="inElement" />}
      {!seedError && resolvedListConfig != null && (!needsDashboard || dashboardResolved) && (
        <ResultsExplorer
          key={`${widgetId}-${seriesIndex}`}
          listConfig={resolvedListConfig}
          initialEntities={seed?.entities}
          seedDateFilters={seedDateFilters}
        />
      )}
    </>
  );
};

export default DashboardResults;
