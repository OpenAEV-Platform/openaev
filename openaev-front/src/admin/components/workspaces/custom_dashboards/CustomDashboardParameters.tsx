import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useMemo } from 'react';

import ScenarioField from '../../../../components/fields/ScenarioField';
import SimulationField from '../../../../components/fields/SimulationField';
import { type CustomDashboardParameters as CustomDashboardParametersType } from '../../../../utils/api-types';
import { CustomDashboardContext, type ParameterOption } from './CustomDashboardContext';
import TimeRangeFilters from './TimeRangeFilters';
import { LAST_QUARTER_TIME_RANGE } from './widgets/configuration/common/TimeRangeUtils';

const CustomDashboardParameters: FunctionComponent = () => {
  const theme = useTheme();
  const { customDashboard, customDashboardParameters, setCustomDashboardParameters, contextId } = useContext(CustomDashboardContext);

  const getParameter = (parameterId: string | undefined) => {
    if (!customDashboard) return undefined;
    if (parameterId) {
      return customDashboardParameters[parameterId];
    } else {
      return undefined;
    }
  };
  const handleParametersValue = (parameterId: string | undefined, value: string) => {
    if (!customDashboard) return;
    if (parameterId) {
      setCustomDashboardParameters((prev) => {
        return {
          ...prev,
          [parameterId]: {
            ...prev[parameterId],
            value,
          },
        };
      });
    }
  };

  const dateParameters: Map<CustomDashboardParametersType['custom_dashboards_parameter_type'], string> = new Map();
  customDashboard?.custom_dashboard_parameters?.forEach((p) => {
    if (['timeRange', 'startDate', 'endDate'].includes(p.custom_dashboards_parameter_type)) {
      if (contextId && !customDashboardParameters[p.custom_dashboards_parameter_id]?.hidden) {
        dateParameters.set(p.custom_dashboards_parameter_type, p.custom_dashboards_parameter_id);
      } else if (!contextId) {
        dateParameters.set(p.custom_dashboards_parameter_type, p.custom_dashboards_parameter_id);
      }
    }
  });

  const renderParameterField = (p: CustomDashboardParametersType, paramOption: ParameterOption | undefined) => {
    switch (p.custom_dashboards_parameter_type) {
      case 'scenario':
        return (
          <ScenarioField
            key={p.custom_dashboards_parameter_id}
            label={p.custom_dashboards_parameter_name}
            value={paramOption?.value}
            onChange={(value: string | undefined) =>
              handleParametersValue(p.custom_dashboards_parameter_id, value ?? '')}
          />
        );
      case 'simulation':
        return (
          <SimulationField
            key={p.custom_dashboards_parameter_id}
            label={p.custom_dashboards_parameter_name}
            value={paramOption?.value}
            onChange={(value: string | undefined) =>
              handleParametersValue(p.custom_dashboards_parameter_id, value ?? '')}
            searchOptionsConfig={paramOption?.searchOptionsConfig}
          />
        );
      default:
        return null;
    }
  };

  const paramsFields = useMemo(() => {
    return (customDashboard?.custom_dashboard_parameters ?? [])
      .filter(p => p.custom_dashboards_parameter_type === 'scenario' || p.custom_dashboards_parameter_type === 'simulation')
      .flatMap((p) => {
        const paramOption = getParameter(p.custom_dashboards_parameter_id);
        if (paramOption?.hidden) return [];
        const field = renderParameterField(p, paramOption);
        return field ? [field] : [];
      });
  }, [customDashboard]);

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fill, minmax(330px, 330px))',
      gap: theme.spacing(2),
    }}
    >
      {paramsFields.length > 0 && (
        <>{paramsFields}</>
      )}
      {dateParameters.size > 0 && (
        <TimeRangeFilters
          timeRangeValue={getParameter(dateParameters.get('timeRange'))?.value ?? LAST_QUARTER_TIME_RANGE}
          handleTimeRange={data => handleParametersValue(dateParameters.get('timeRange'), data)}
          startDateValue={getParameter(dateParameters.get('startDate'))?.value}
          handleStartDate={data => handleParametersValue(dateParameters.get('startDate'), data)}
          endDateValue={getParameter(dateParameters.get('endDate'))?.value}
          handleEndDate={data => handleParametersValue(dateParameters.get('endDate'), data)}
        />

      )}
    </div>

  );
};

export default CustomDashboardParameters;
