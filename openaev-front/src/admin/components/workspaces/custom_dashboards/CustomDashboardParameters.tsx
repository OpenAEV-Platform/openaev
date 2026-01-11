import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';

import ScenarioField from '../../../../components/fields/ScenarioField';
import SimulationField from '../../../../components/fields/SimulationField';
import { type CustomDashboardParameters as CustomDashboardParametersType } from '../../../../utils/api-types';
import { CustomDashboardContext } from './CustomDashboardContext';
import TimeRangeFilters from './TimeRangeFilters';
import { LAST_QUARTER_TIME_RANGE } from './widgets/configuration/common/TimeRangeUtils';

const CustomDashboardParameters: FunctionComponent = () => {
  const theme = useTheme();
  const { customDashboard, customDashboardParameters, setCustomDashboardParameters, contextId } = useContext(CustomDashboardContext);

  const handleParametersValue = (parameterId: string, value: string) => {
    if (!parameterId) return;
    setCustomDashboardParameters(prev => ({
      ...prev,
      [parameterId]: {
        ...prev[parameterId],
        value,
      },
    }));
  };

  // Build date parameters map
  const dateParameters = new Map<CustomDashboardParametersType['custom_dashboards_parameter_type'], string>();
  customDashboard?.custom_dashboard_parameters?.forEach((p) => {
    if (['timeRange', 'startDate', 'endDate'].includes(p.custom_dashboards_parameter_type)) {
      if (contextId && !customDashboardParameters[p.custom_dashboards_parameter_id]?.hidden) {
        dateParameters.set(p.custom_dashboards_parameter_type, p.custom_dashboards_parameter_id);
      } else if (!contextId) {
        dateParameters.set(p.custom_dashboards_parameter_type, p.custom_dashboards_parameter_id);
      }
    }
  });

  // Build parameter fields
  const paramsFields = customDashboard?.custom_dashboard_parameters
    ?.filter(p => p.custom_dashboards_parameter_type === 'scenario' || p.custom_dashboards_parameter_type === 'simulation')
    .flatMap((p) => {
      const paramOption = customDashboardParameters[p.custom_dashboards_parameter_id];
      if (paramOption?.hidden) return [];

      if (p.custom_dashboards_parameter_type === 'scenario') {
        return [(
          <ScenarioField
            key={p.custom_dashboards_parameter_id}
            label={p.custom_dashboards_parameter_name}
            value={paramOption?.value}
            onChange={newValue => handleParametersValue(p.custom_dashboards_parameter_id, newValue ?? '')}
          />
        )];
      }

      if (p.custom_dashboards_parameter_type === 'simulation') {
        return [(
          <SimulationField
            key={p.custom_dashboards_parameter_id}
            label={p.custom_dashboards_parameter_name}
            value={paramOption?.value}
            onChange={newValue => handleParametersValue(p.custom_dashboards_parameter_id, newValue ?? '')}
            searchOptionsConfig={paramOption?.searchOptionsConfig}
          />
        )];
      }

      return [];
    }) ?? [];

  const timeRangeParamId = dateParameters.get('timeRange');
  const startDateParamId = dateParameters.get('startDate');
  const endDateParamId = dateParameters.get('endDate');

  const handleTimeRange = (data: string) => {
    if (timeRangeParamId) handleParametersValue(timeRangeParamId, data);
  };

  const handleStartDate = (data: string) => {
    if (startDateParamId) handleParametersValue(startDateParamId, data);
  };

  const handleEndDate = (data: string) => {
    if (endDateParamId) handleParametersValue(endDateParamId, data);
  };

  const timeRangeValue = timeRangeParamId ? (customDashboardParameters[timeRangeParamId]?.value ?? LAST_QUARTER_TIME_RANGE) : LAST_QUARTER_TIME_RANGE;
  const startDateValue = startDateParamId ? customDashboardParameters[startDateParamId]?.value : undefined;
  const endDateValue = endDateParamId ? customDashboardParameters[endDateParamId]?.value : undefined;

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fill, minmax(330px, 330px))',
      gap: theme.spacing(2),
    }}
    >
      {paramsFields.length > 0 && paramsFields}
      {dateParameters.size > 0 && (
        <TimeRangeFilters
          timeRangeValue={timeRangeValue}
          handleTimeRange={handleTimeRange}
          startDateValue={startDateValue}
          handleStartDate={handleStartDate}
          endDateValue={endDateValue}
          handleEndDate={handleEndDate}
        />
      )}
    </div>
  );
};

export default CustomDashboardParameters;
