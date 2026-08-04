import type { AxiosResponse } from 'axios';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import Loader from '../../../../components/Loader';
import type {
  CustomDashboard,
  EsAttackPath, EsAvgs,
  EsCountInterval, EsEntities,
  EsSeries, Pagination,
  WidgetToEntitiesInput,
  WidgetToEntitiesOutput,
} from '../../../../utils/api-types';
import CustomDashboardComponent from './CustomDashboardComponent';
import { CustomDashboardContext, type CustomDashboardContextType, type ParameterOption, type WidgetResultsConf } from './CustomDashboardContext';
import { LAST_QUARTER_TIME_RANGE } from './widgets/configuration/common/TimeRangeUtils';

const MIN_LOADING_TIME = 800; // Minimum time to show loader to avoid blinking

/**
 * Where the dashboard definition can be re-fetched from by the full-page
 * results explorer (each surface reads dashboards through its own
 * permission-scoped endpoint).
 */
export interface ResultsSource {
  source: 'workspace' | 'tenant' | 'simulation' | 'scenario';
  contextId?: string;
}

interface CustomDashboardConfiguration {
  customDashboardId?: CustomDashboard['custom_dashboard_id'];
  paramLocalStorageKey: string;
  resultsSource?: ResultsSource;
  paramsBuilder?: (dashboardParams: CustomDashboard['custom_dashboard_parameters'], params: Record<string, ParameterOption>) => Promise<Record<string, ParameterOption>> | Record<string, ParameterOption>;
  parentContextId?: string;
  canChooseDashboard?: boolean;
  handleSelectNewDashboard?: (dashboardId: string) => void;
  fetchCustomDashboard: () => Promise<AxiosResponse<CustomDashboard>>;
  fetchCount: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsCountInterval>>;
  fetchAverage: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsAvgs>>;
  fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsSeries[]>>;
  fetchEntities: (widgetId: string, params: Record<string, string | undefined>, pagination?: Pagination) => Promise<AxiosResponse<EsEntities>>;
  fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => Promise<AxiosResponse<WidgetToEntitiesOutput>>;
  fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsAttackPath[]>>;
}

interface Props {
  topSlot?: React.ReactNode;
  bottomSlot?: React.ReactNode;
  noDashboardSlot?: React.ReactNode;
  readOnly?: boolean;
  configuration: CustomDashboardConfiguration;
}

const CustomDashboardWrapper = ({
  configuration,
  topSlot,
  bottomSlot,
  noDashboardSlot,
  readOnly = true,
}: Props) => {
  const {
    customDashboardId,
    paramLocalStorageKey,
    resultsSource,
    paramsBuilder,
    parentContextId: contextId,
    canChooseDashboard,
    handleSelectNewDashboard,
    fetchCustomDashboard,
    fetchCount,
    fetchAverage,
    fetchSeries,
    fetchEntities,
    fetchEntitiesRuntime,
    fetchAttackPaths,
  } = configuration || {};

  const [customDashboard, setCustomDashboard] = useState<CustomDashboard>();
  const [parametersLocalStorage, setParametersLocalStorage] = useLocalStorage<Record<string, ParameterOption>>(paramLocalStorageKey, {});
  const [parameters, setParameters] = useState<Record<string, ParameterOption>>({});
  const [dataReady, setDataReady] = useState(false);
  const [_gridReady, setGridReady] = useState(false);
  const loadingStartTime = useRef<number>(Date.now());
  const dataReadyTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => {
    return () => {
      // Compare against undefined rather than truthiness: a valid timer handle
      // can be 0 in some implementations/polyfills.
      if (dataReadyTimeoutRef.current !== undefined) {
        clearTimeout(dataReadyTimeoutRef.current);
        dataReadyTimeoutRef.current = undefined;
      }
    };
  }, []);

  const navigate = useNavigate();
  const location = useLocation();

  // Every widget element click lands on the full-page results explorer (same
  // UX as the built-in home dashboard): the clicked scope, the dashboard
  // source (so the explorer can re-read the widget definition through the
  // right permission-scoped endpoint), the current parameter values and a
  // back link to this dashboard all travel through the URL.
  const handleOpenWidgetResults = useCallback((conf: WidgetResultsConf) => {
    const params = new URLSearchParams();
    params.set('widget_id', conf.widgetId);
    params.set('series_index', (conf.series_index ?? '').toString());
    // Totals spanning several series carry every contributing index, so the
    // drilled list resolves to exactly the documents the tile counted.
    (conf.series_indexes ?? []).forEach(index => params.append('series_indexes', index.toString()));
    const source = resultsSource ?? { source: 'workspace' as const };
    params.set('source', source.source);
    if (source.contextId) {
      params.set('context_id', source.contextId);
    }
    if (customDashboardId) {
      params.set('dashboard_id', customDashboardId);
    }
    Object.entries(parameters).forEach(([key, option]) => {
      if (option.value) {
        params.set(`param.${key}`, option.value);
      }
    });
    if (conf.filter_values_map) {
      // One URL param per value: comma-joining would corrupt values containing
      // commas, and empty arrays must not produce an empty-string filter value.
      Object.entries(conf.filter_values_map).forEach(([key, values]) => {
        (values ?? []).forEach(value => params.append(key, value));
      });
    }
    // Router-relative path (react-router strips the tenant basename):
    // window.location would double the tenant prefix on the way back.
    params.set('back', `${location.pathname}${location.search}`);
    navigate(`/admin/results?${params.toString()}`);
  }, [navigate, location, resultsSource, customDashboardId, parameters]);

  const setDataReadyWithDelay = () => {
    const elapsed = Date.now() - loadingStartTime.current;
    const remainingTime = Math.max(0, MIN_LOADING_TIME - elapsed);
    if (dataReadyTimeoutRef.current !== undefined) {
      clearTimeout(dataReadyTimeoutRef.current);
    }
    dataReadyTimeoutRef.current = setTimeout(() => setDataReady(true), remainingTime);
  };

  // Compute loading state: show loader until data is ready
  // Note: gridReady is handled internally by CustomDashboardReactLayout with visibility:hidden
  const loading = !dataReady;

  useEffect(() => {
    if (!customDashboard) {
      return;
    }
    const handleParametersInitialization = async () => {
      let params: Record<string, ParameterOption> = { ...parametersLocalStorage };
      customDashboard?.custom_dashboard_parameters?.forEach((p: {
        custom_dashboards_parameter_type: string;
        custom_dashboards_parameter_id: string;
      }) => {
        if (p.custom_dashboards_parameter_type === 'timeRange' && !parametersLocalStorage[p.custom_dashboards_parameter_id]) {
          params[p.custom_dashboards_parameter_id] = {
            value: LAST_QUARTER_TIME_RANGE,
            hidden: false,
          };
        }
      });
      if (paramsBuilder) {
        params = await paramsBuilder(customDashboard.custom_dashboard_parameters, params);
      }
      return params;
    };
    handleParametersInitialization().then((params) => {
      setParameters(params || {});
      setDataReadyWithDelay();
    });
  }, [customDashboard, parametersLocalStorage, paramsBuilder]);

  useEffect(() => {
    if (customDashboardId) {
      // Reset loading state when dashboard ID changes
      setDataReady(false);
      setGridReady(false);
      loadingStartTime.current = Date.now();
      fetchCustomDashboard()
        .then((response) => {
          const dashboard = response.data;
          if (!dashboard) {
            // Dashboard not found, mark as ready (will show no dashboard message)
            setDataReadyWithDelay();
            setGridReady(true); // No grid to wait for
            return;
          }
          setCustomDashboard(dashboard);
        })
        .catch(() => {
          // Fetch failed, mark as ready (will show error or no dashboard)
          setDataReadyWithDelay();
          setGridReady(true); // No grid to wait for
        });
    } else {
      // No dashboard ID, mark as ready immediately
      setDataReadyWithDelay();
      setGridReady(true); // No grid to wait for
      setCustomDashboard(undefined);
    }
  }, [customDashboardId, fetchCustomDashboard]);

  const contextValue: CustomDashboardContextType = useMemo(() => ({
    customDashboard,
    setCustomDashboard,
    customDashboardParameters: parameters,
    setCustomDashboardParameters: setParametersLocalStorage,
    contextId,
    canChooseDashboard,
    handleSelectNewDashboard,
    fetchEntities,
    fetchEntitiesRuntime,
    fetchCount,
    fetchAverage,
    fetchSeries,
    fetchAttackPaths,
    openWidgetResults: handleOpenWidgetResults,
    setGridReady,
  }), [
    customDashboard,
    parameters,
    setParametersLocalStorage,
    contextId,
    canChooseDashboard,
    handleSelectNewDashboard,
    fetchEntities,
    fetchEntitiesRuntime,
    fetchCount,
    fetchAverage,
    fetchSeries,
    fetchAttackPaths,
    handleOpenWidgetResults,
  ]);

  if (loading) {
    return <Loader />;
  }

  return (
    <CustomDashboardContext.Provider value={contextValue}>
      {topSlot}
      <CustomDashboardComponent
        readOnly={readOnly}
        noDashboardSlot={noDashboardSlot}
      />
      {bottomSlot}
    </CustomDashboardContext.Provider>
  );
};

export default CustomDashboardWrapper;
