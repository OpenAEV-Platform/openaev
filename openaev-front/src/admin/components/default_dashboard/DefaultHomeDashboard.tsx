import { RefreshOutlined } from '@mui/icons-material';
import { Box, IconButton, MenuItem, Select, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { fetchSecurityPlatforms } from '../../../actions/assets/securityPlatform-actions';
import { adHocAverage, adHocCount, adHocEntities, adHocEntitiesRuntime, adHocSeries } from '../../../actions/dashboards/dashboard-action';
import { useFormatter } from '../../../components/i18n';
import { type CustomDashboard, type Widget, type WidgetToEntitiesInput } from '../../../utils/api-types';
import { useBulkOperationsFinishedCount } from '../../../utils/bulkOperations';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import limitConcurrency from '../../../utils/limitConcurrency';
import { CustomDashboardContext, type CustomDashboardContextType, type WidgetResultsConf } from '../workspaces/custom_dashboards/CustomDashboardContext';
import CustomDashboardReactLayout from '../workspaces/custom_dashboards/CustomDashboardReactLayout';
import { getTimeRangeItems } from '../workspaces/custom_dashboards/widgets/configuration/common/TimeRangeUtils';
import {
  buildDefaultHomeWidgets,
  type DefaultTimeRange,
  PLATFORM_DEFAULT_DASHBOARD_ID,
} from './defaultHomeWidgets';

const NOW = new Date().toISOString();

// The dashboard fires one ad-hoc query per widget (20+) the moment it mounts.
// While the platform is still warming up each query can take seconds, and
// letting them all run at once exhausts the browser's ~6 connections per
// origin - lazy route chunks then queue behind them and the left menu appears
// frozen. Capping the widget queries keeps connections free for navigation.
const limitWidgetQueries = limitConcurrency(4);

/**
 * The hardcoded "Platform default" home dashboard. Reuses the full custom
 * dashboard engine (grid layout, widget wrappers, visualizations) with
 * non-persisted widgets whose data is fetched through the ad-hoc dashboard
 * API. Widget clicks navigate straight to the relevant platform pages.
 */
const DefaultHomeDashboard = () => {
  const theme = useTheme();
  const { t, locale } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  // Connected security platforms power the orbiting nodes in the command center.
  useDataLoader(() => {
    dispatch(fetchSecurityPlatforms());
  });

  const [timeRange, setTimeRange] = useLocalStorage<DefaultTimeRange>('default-home-dashboard-time-range', 'LAST_QUARTER');
  const [refreshCount, setRefreshCount] = useState(0);

  // Massive operations no longer stream one event per deleted entity (which used to force a
  // widget refresh per delete): refresh the engine-backed widgets once per finished operation,
  // after a short delay so the search engine has flushed the deletions. Only INCREASES after
  // mount count: a nonzero value at mount is history (operations finished earlier in the
  // session), and refreshing for it would refetch every widget right after the initial load.
  const finishedBulkOperations = useBulkOperationsFinishedCount();
  const seenFinishedOperations = useRef(finishedBulkOperations);
  useEffect(() => {
    if (finishedBulkOperations <= seenFinishedOperations.current) {
      return undefined;
    }
    seenFinishedOperations.current = finishedBulkOperations;
    const timeout = setTimeout(() => setRefreshCount(count => count + 1), 2500);
    return () => clearTimeout(timeout);
  }, [finishedBulkOperations]);

  // Titles are localized at build time so the grid headers and the results page agree.
  // `t` from useFormatter() is a NEW function on every render, so it must NOT be a
  // dependency here: every recompute cascades into new fetch functions in the
  // context value and refetches all ~20 widgets (same rule as DefaultHomeResults).
  // `locale` is the stable signal that t's output actually changed, so a runtime
  // language switch still recomputes the titles (one refetch, but only then).
  const widgets = useMemo(() => buildDefaultHomeWidgets(timeRange, t), [timeRange, locale]);

  const widgetById = useMemo(() => {
    const map = new Map<string, Widget>();
    widgets.forEach(w => map.set(w.widget_id, w));
    return map;
  }, [widgets]);

  // Deliberately not keyed on the unstable `t`; `locale` covers language switches
  // (see the widgets memo above).
  const customDashboard: CustomDashboard = useMemo(() => ({
    custom_dashboard_id: PLATFORM_DEFAULT_DASHBOARD_ID,
    custom_dashboard_name: t('Platform default'),
    custom_dashboard_widgets: widgets,
    custom_dashboard_parameters: [],
    custom_dashboard_created_at: NOW,
    custom_dashboard_updated_at: NOW,
    listened: false,
  }), [widgets, locale]);

  const widgetOf = useCallback((widgetId: string) => {
    const widget = widgetById.get(widgetId);
    if (!widget) {
      throw new Error(`Unknown default dashboard widget: ${widgetId}`);
    }
    return widget;
  }, [widgetById]);

  // Every widget click lands on the full-page results explorer: no drawer,
  // no dashboard re-render, a real navigable page with the scoped entities.
  const openWidgetResults = useCallback((conf: WidgetResultsConf) => {
    const params = new URLSearchParams();
    params.set('widget_id', conf.widgetId);
    params.set('series_index', (conf.series_index ?? '').toString());
    // Totals spanning several series carry every contributing index, so the
    // drilled list resolves to exactly the documents the tile counted.
    (conf.series_indexes ?? []).forEach(index => params.append('series_indexes', index.toString()));
    if (conf.filter_values_map) {
      // One URL param per value: comma-joining would corrupt values containing commas,
      // and empty arrays must not produce an empty-string filter value.
      Object.entries(conf.filter_values_map).forEach(([key, values]) => {
        (values ?? []).forEach(value => params.append(key, value));
      });
    }
    params.set('back', '/admin');
    navigate(`/admin/results?${params.toString()}`);
  }, [navigate]);

  const contextValue: CustomDashboardContextType = useMemo(() => ({
    customDashboard,
    setCustomDashboard: () => {},
    // the refresh counter is part of the parameters map so every widget
    // wrapper refetches when the user hits refresh
    customDashboardParameters: {
      refresh: {
        value: String(refreshCount),
        hidden: true,
      },
    },
    setCustomDashboardParameters: () => {},
    fetchSeries: (widgetId: string) => limitWidgetQueries(() => adHocSeries(widgetOf(widgetId).widget_config)),
    fetchCount: (widgetId: string) => limitWidgetQueries(() => adHocCount(widgetOf(widgetId).widget_config)),
    fetchEntities: (widgetId: string, _params: Record<string, string | undefined>, pagination?: Parameters<typeof adHocEntities>[2]) =>
      limitWidgetQueries(() => adHocEntities(widgetOf(widgetId).widget_config, undefined, pagination)),
    fetchAverage: (widgetId: string) => limitWidgetQueries(() => adHocAverage(widgetOf(widgetId).widget_config)),
    fetchAttackPaths: () => Promise.reject(new Error('Not supported on the default dashboard')),
    // drill-downs convert the ad-hoc widget into a scoped entity list
    fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => {
      const widget = widgetOf(widgetId);
      return limitWidgetQueries(() => adHocEntitiesRuntime(widget.widget_type, widget.widget_config, input));
    },
    openWidgetResults,
    setGridReady: () => {},
  }), [customDashboard, widgetOf, refreshCount, openWidgetResults]);

  return (
    <CustomDashboardContext.Provider value={contextValue}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1.5),
          marginBottom: theme.spacing(2),
        }}
      >
        <Box
          sx={{
            'width': 8,
            'height': 8,
            'borderRadius': '50%',
            'backgroundColor': 'secondary.main',
            'boxShadow': `0 0 8px ${theme.palette.secondary.main}`,
            'animation': 'default-home-pulse 2s ease-in-out infinite',
            '@keyframes default-home-pulse': {
              '0%, 100%': { opacity: 1 },
              '50%': { opacity: 0.3 },
            },
          }}
        />
        <Typography
          sx={{
            fontFamily: '"Geologica", sans-serif',
            fontSize: 13,
            fontWeight: 600,
            letterSpacing: '0.1em',
            textTransform: 'uppercase',
          }}
        >
          {t('Adversarial exposure overview')}
        </Typography>
        <div style={{ flex: 1 }} />
        <Select
          variant="standard"
          size="small"
          value={timeRange}
          onChange={e => setTimeRange(e.target.value as DefaultTimeRange)}
          sx={{ minWidth: 160 }}
        >
          {getTimeRangeItems()
            .filter(item => item.value !== 'CUSTOM')
            .map(item => (
              <MenuItem key={item.value} value={item.value}>
                {t(item.label_key)}
              </MenuItem>
            ))}
        </Select>
        <Tooltip title={t('Refresh')}>
          <IconButton
            size="small"
            color="primary"
            onClick={() => setRefreshCount(c => c + 1)}
          >
            <RefreshOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      </div>
      <CustomDashboardReactLayout readOnly />
    </CustomDashboardContext.Provider>
  );
};

export default DefaultHomeDashboard;
