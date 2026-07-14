import { RefreshOutlined } from '@mui/icons-material';
import { Box, IconButton, MenuItem, Select, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { fetchSecurityPlatforms } from '../../../actions/assets/securityPlatform-actions';
import { adHocAverage, adHocCount, adHocEntities, adHocSeries } from '../../../actions/dashboards/dashboard-action';
import { useFormatter } from '../../../components/i18n';
import { type CustomDashboard, type Widget } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { CustomDashboardContext, type CustomDashboardContextType } from '../workspaces/custom_dashboards/CustomDashboardContext';
import CustomDashboardReactLayout from '../workspaces/custom_dashboards/CustomDashboardReactLayout';
import { getTimeRangeItems } from '../workspaces/custom_dashboards/widgets/configuration/common/TimeRangeUtils';
import {
  buildDefaultHomeWidgets,
  DEFAULT_HOME_WIDGET_ROUTES,
  type DefaultTimeRange,
  PLATFORM_DEFAULT_DASHBOARD_ID,
} from './defaultHomeWidgets';

const NOW = new Date().toISOString();

/**
 * The hardcoded "Platform default" home dashboard. Reuses the full custom
 * dashboard engine (grid layout, widget wrappers, visualizations) with
 * non-persisted widgets whose data is fetched through the ad-hoc dashboard
 * API. Widget clicks navigate straight to the relevant platform pages.
 */
const DefaultHomeDashboard = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  // Connected security platforms power the orbiting nodes in the command center.
  useDataLoader(() => {
    dispatch(fetchSecurityPlatforms());
  });

  const [timeRange, setTimeRange] = useLocalStorage<DefaultTimeRange>('default-home-dashboard-time-range', 'LAST_QUARTER');
  const [refreshCount, setRefreshCount] = useState(0);

  const widgets = useMemo(() => buildDefaultHomeWidgets(timeRange), [timeRange]);

  const widgetConfigById = useMemo(() => {
    const map = new Map<string, Widget['widget_config']>();
    widgets.forEach(w => map.set(w.widget_id, w.widget_config));
    return map;
  }, [widgets]);

  const customDashboard: CustomDashboard = useMemo(() => ({
    custom_dashboard_id: PLATFORM_DEFAULT_DASHBOARD_ID,
    custom_dashboard_name: t('Platform default'),
    custom_dashboard_widgets: widgets,
    custom_dashboard_parameters: [],
    custom_dashboard_created_at: NOW,
    custom_dashboard_updated_at: NOW,
    listened: false,
  }), [widgets, t]);

  const configOf = useCallback((widgetId: string) => {
    const config = widgetConfigById.get(widgetId);
    if (!config) {
      throw new Error(`Unknown default dashboard widget: ${widgetId}`);
    }
    return config;
  }, [widgetConfigById]);

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
    fetchSeries: (widgetId: string) => adHocSeries(configOf(widgetId)),
    fetchCount: (widgetId: string) => adHocCount(configOf(widgetId)),
    fetchEntities: (widgetId: string, _params: Record<string, string | undefined>, pagination?: Parameters<typeof adHocEntities>[2]) =>
      adHocEntities(configOf(widgetId), undefined, pagination),
    fetchAverage: (widgetId: string) => adHocAverage(configOf(widgetId)),
    fetchAttackPaths: () => Promise.reject(new Error('Not supported on the default dashboard')),
    fetchEntitiesRuntime: () => Promise.reject(new Error('Not supported on the default dashboard')),
    // clicking a widget jumps straight to the platform page owning the data
    openWidgetDataDrawer: (conf) => {
      const route = DEFAULT_HOME_WIDGET_ROUTES[conf.widgetId];
      if (route) {
        navigate(route);
      }
    },
    closeWidgetDataDrawer: () => {},
    setGridReady: () => {},
  }), [customDashboard, configOf, navigate, refreshCount]);

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
        <Typography
          sx={{
            fontSize: 12,
            fontStyle: 'italic',
            color: 'text.secondary',
          }}
        >
          {t('Platform default')}
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
