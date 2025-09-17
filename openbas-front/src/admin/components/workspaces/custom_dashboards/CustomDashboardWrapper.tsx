import type { AxiosResponse } from 'axios';
import { useEffect, useMemo, useState } from 'react';
import { useLocalStorage, useReadLocalStorage } from 'usehooks-ts';

import Loader from '../../../../components/Loader';
import type { CustomDashboard, EsAttackPath, EsBase, EsSeries } from '../../../../utils/api-types';
import CustomDashboardComponent from './CustomDashboardComponent';
import { CustomDashboardContext, type CustomDashboardContextType, type ParameterOption } from './CustomDashboardContext';
import { LAST_QUARTER_TIME_RANGE } from './widgets/configuration/common/TimeRangeUtils';

interface CustomDashboardConfiguration {
  customDashboardId?: CustomDashboard['custom_dashboard_id'];
  paramLocalStorageKey: string;
  paramsBuilder?: (dashboardParams: CustomDashboard['custom_dashboard_parameters'], params: Record<string, ParameterOption>) => Record<string, ParameterOption>;
  parentContextId?: string;
  canChooseDashboard?: boolean;
  handleSelectNewDashboard?: (dashboardId: string) => void; // ==onCustomDashboardIdChange
  fetchCustomDashboard: () => Promise<AxiosResponse<CustomDashboard>>;
  fetchCount: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<number>>;
  fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsSeries[]>>;
  fetchEntities: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsBase[]>>;
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
    fetchAttackPaths,
  } = configuration || {};
  const [customDashboard, setCustomDashboard] = useState<CustomDashboard>();
  const parametersLocalStorage = useReadLocalStorage<Record<string, ParameterOption>>(paramLocalStorageKey);
  const [, setParametersLocalStorage] = useLocalStorage<Record<string, ParameterOption>>(paramLocalStorageKey, {});
  const [parameters, setParameters] = useState<Record<string, ParameterOption>>({});
  const [loading, setLoading] = useState(true);

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
    fetchCount,
    fetchSeries,
    fetchAttackPaths,
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
