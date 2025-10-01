import type { AxiosResponse } from 'axios';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useLocalStorage, useReadLocalStorage } from 'usehooks-ts';

import Loader from '../../../../components/Loader';
import type {
  CustomDashboard,
  EsAttackPath,
  EsBase, EsCountInterval,
  EsSeries,
  WidgetToEntitiesInput,
  WidgetToEntitiesOutput,
} from '../../../../utils/api-types';
import CustomDashboardComponent from './CustomDashboardComponent';
import { CustomDashboardContext, type CustomDashboardContextType, type ParameterOption } from './CustomDashboardContext';
import type { WidgetDataDrawerConf } from './widgetDataDrawer/WidgetDataDrawer';
import { LAST_QUARTER_TIME_RANGE } from './widgets/configuration/common/TimeRangeUtils';

interface CustomDashboardConfiguration {
  customDashboardId?: CustomDashboard['custom_dashboard_id'];
  paramLocalStorageKey: string;
  paramsBuilder?: (dashboardParams: CustomDashboard['custom_dashboard_parameters'], params: Record<string, ParameterOption>) => Record<string, ParameterOption>;
  parentContextId?: string;
  canChooseDashboard?: boolean;
  handleSelectNewDashboard?: (dashboardId: string) => void; // ==onCustomDashboardIdChange
  fetchCustomDashboard: () => Promise<AxiosResponse<CustomDashboard>>;
  fetchCount: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsCountInterval>>;
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
    fetchSeries,
    fetchEntities,
    fetchEntitiesRuntime,
    fetchAttackPaths,
  } = configuration || {};
  const [customDashboard, setCustomDashboard] = useState<CustomDashboard>();
  const parametersLocalStorage = useReadLocalStorage<Record<string, ParameterOption>>(paramLocalStorageKey);
  const [, setParametersLocalStorage] = useLocalStorage<Record<string, ParameterOption>>(paramLocalStorageKey, {});
  const [parameters, setParameters] = useState<Record<string, ParameterOption>>({});
  const [loading, setLoading] = useState(true);

  const [searchParams, setSearchParams] = useSearchParams();
  const handleOpenWidgetDataDrawer = (conf: WidgetDataDrawerConf) => {
    searchParams.set('widget_id', conf.widgetId);
    searchParams.set('series_index', (conf.series_index ?? '').toString());
    searchParams.set('filter_values', (conf.filter_values ?? []).join(','));
    setSearchParams(searchParams, { replace: true });
  };
  const handleCloseWidgetDataDrawer = () => {
    searchParams.delete('widget_id');
    searchParams.delete('series_index');
    searchParams.delete('filter_values');
    setSearchParams(searchParams, { replace: true });
  };

  useEffect(() => {
    if (customDashboard) {
      if (!parametersLocalStorage) {
        setParametersLocalStorage({});
      } else {
        let params: Record<string, ParameterOption> = parametersLocalStorage;
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
          params = paramsBuilder(customDashboard.custom_dashboard_parameters, params);
        }
        setParameters(params);
        setLoading(false);
      }
    }
  }, [customDashboard, parametersLocalStorage]);

  useEffect(() => {
    if (customDashboardId) {
      fetchCustomDashboard().then((response) => {
        const dashboard = response.data;
        if (!dashboard) {
          return;
        }
        setCustomDashboard(dashboard);
      });
    } else {
      setLoading(false);
    }
  }, [customDashboardId]);

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
    fetchSeries,
    fetchAttackPaths,
    openWidgetDataDrawer: handleOpenWidgetDataDrawer,
    closeWidgetDataDrawer: handleCloseWidgetDataDrawer,
  }), [customDashboard, setCustomDashboard, parameters, setParametersLocalStorage]);

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
