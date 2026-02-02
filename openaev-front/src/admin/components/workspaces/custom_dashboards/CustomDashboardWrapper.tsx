import type { AxiosResponse } from 'axios';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useLocalStorage, useReadLocalStorage } from 'usehooks-ts';

import Loader from '../../../../components/Loader';
import type {
  CustomDashboard,
  EsAttackPath, EsAvgs,
  EsBase, EsCountInterval,
  EsSeries,
  WidgetToEntitiesInput,
  WidgetToEntitiesOutput,
} from '../../../../utils/api-types';
import CustomDashboardComponent from './CustomDashboardComponent';
import { CustomDashboardContext, type CustomDashboardContextType, type ParameterOption } from './CustomDashboardContext';
import type { WidgetDataDrawerConf } from './widgetDataDrawer/WidgetDataDrawer';
import { LAST_QUARTER_TIME_RANGE } from './widgets/configuration/common/TimeRangeUtils';

const MIN_LOADING_TIME = 800; // Minimum time to show loader to avoid blinking

interface CustomDashboardConfiguration {
  customDashboardId?: CustomDashboard['custom_dashboard_id'];
  paramLocalStorageKey: string;
  paramsBuilder?: (dashboardParams: CustomDashboard['custom_dashboard_parameters'], params: Record<string, ParameterOption>) => Promise<Record<string, ParameterOption>> | Record<string, ParameterOption>;
  parentContextId?: string;
  canChooseDashboard?: boolean;
  handleSelectNewDashboard?: (dashboardId: string) => void;
  fetchCustomDashboard: () => Promise<AxiosResponse<CustomDashboard>>;
  fetchCount: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsCountInterval>>;
  fetchAverage: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsAvgs>>;
  fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsSeries[]>>;
  fetchEntities: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsBase[]>>;
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
  const parametersLocalStorage = useReadLocalStorage<Record<string, ParameterOption>>(paramLocalStorageKey);
  const [, setParametersLocalStorage] = useLocalStorage<Record<string, ParameterOption>>(paramLocalStorageKey, {});
  const [parameters, setParameters] = useState<Record<string, ParameterOption>>({});
  const [dataReady, setDataReady] = useState(false);
  const [_gridReady, setGridReady] = useState(false);
  const loadingStartTime = useRef<number>(Date.now());

  const [, setSearchParams] = useSearchParams();

  const handleOpenWidgetDataDrawer = (conf: WidgetDataDrawerConf) => {
    setSearchParams((prevParams) => {
      const newParams = new URLSearchParams(prevParams);
      newParams.set('widget_id', conf.widgetId);
      newParams.set('series_index', (conf.series_index ?? '').toString());
      newParams.set('filter_values', (conf.filter_values ?? []).join(','));
      return newParams;
    }, { replace: true });
  };

  const handleCloseWidgetDataDrawer = () => {
    setSearchParams((prevParams) => {
      const newParams = new URLSearchParams(prevParams);
      newParams.delete('widget_id');
      newParams.delete('series_index');
      newParams.delete('filter_values');
      return newParams;
    }, { replace: true });
  };

  const setDataReadyWithDelay = () => {
    const elapsed = Date.now() - loadingStartTime.current;
    const remainingTime = Math.max(0, MIN_LOADING_TIME - elapsed);
    setTimeout(() => setDataReady(true), remainingTime);
  };

  // Compute loading state: show loader until data is ready
  // Note: gridReady is handled internally by CustomDashboardReactLayout with visibility:hidden
  const loading = !dataReady;

  useEffect(() => {
    if (!customDashboard) {
      return;
    }
    if (!parametersLocalStorage) {
      setParametersLocalStorage({});
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
  }, [customDashboard, parametersLocalStorage, paramsBuilder, setParametersLocalStorage]);

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
    openWidgetDataDrawer: handleOpenWidgetDataDrawer,
    closeWidgetDataDrawer: handleCloseWidgetDataDrawer,
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
    fetchSeries,
    fetchAttackPaths,
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
