import { useEffect, useMemo, useState } from 'react';
import { useLocalStorage } from 'usehooks-ts';

import { fetchCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import Loader from '../../../../components/Loader';
import type { CustomDashboard } from '../../../../utils/api-types';
import CustomDashboardComponent from './CustomDashboardComponent';
import { CustomDashboardContext, type CustomDashboardContextType, type ParameterOption } from './CustomDashboardContext';

interface CustomDashboardConfiguration {
  customDashboardId?: CustomDashboard['custom_dashboard_id'];
  paramLocalStorageKey: string;
  paramsBuilder?: (dashboardParams: CustomDashboard['custom_dashboard_parameters'], params: Record<string, ParameterOption>) => Record<string, ParameterOption>;
  parentContextId?: string;
  canChooseDashboard?: boolean;
  handleSelectNewDashboard?: (dashboardId: string) => void; // ==onCustomDashboardIdChange
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
  const { customDashboardId, paramLocalStorageKey, paramsBuilder, parentContextId: contextId, canChooseDashboard, handleSelectNewDashboard } = configuration || {};
  const [customDashboard, setCustomDashboard] = useState<CustomDashboard>();
  const [parameters, setParameters] = useLocalStorage<Record<string, ParameterOption>>(paramLocalStorageKey, Object.fromEntries(new Map()));
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (customDashboardId) {
      fetchCustomDashboard(customDashboardId).then((response) => {
        const dashboard = response.data;
        if (!dashboard) {
          return;
        }
        setCustomDashboard(dashboard);
        if (paramsBuilder) {
          const builtParams = paramsBuilder(dashboard.custom_dashboard_parameters, parameters);
          setParameters(builtParams);
        }
        setLoading(false);
      });
    } else {
      setCustomDashboard(undefined);
      setLoading(false);
    }
  }, [customDashboardId]);

  const contextValue: CustomDashboardContextType = useMemo(() => ({
    customDashboard,
    setCustomDashboard,
    customDashboardParameters: parameters,
    setCustomDashboardParameters: setParameters,
    contextId,
    canChooseDashboard,
    handleSelectNewDashboard,
  }), [customDashboard, setCustomDashboard, parameters, setParameters]);

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
