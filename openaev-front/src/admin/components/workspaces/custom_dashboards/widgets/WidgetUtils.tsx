import { AccountTree, List, TableChart } from '@mui/icons-material';
import { AlignHorizontalLeft, ChartBar, ChartDonut, ChartLine, Counter } from 'mdi-material-ui';

import {
  type CustomDashboardParameters,
  type Exercise,
  type Filter,
  type FilterGroup,
  type InjectExpectation, type Series,
} from '../../../../../utils/api-types';
import {
  type HistogramWidget,
  type Widget,
  type WidgetInput,
} from '../../../../../utils/api-types-custom';
import { createGroupOption, type GroupOption } from '../../../../../utils/Option';

export type WidgetInputWithoutLayout = Omit<WidgetInput, 'widget_layout'>;
export type StepType = ('type' | 'series' | 'parameters');
export const steps: StepType[] = ['type', 'series', 'parameters'];
export const lastStepIndex = steps.length - 1;
const defaultSteps: StepType[] = ['type', 'series', 'parameters'];
const defaultModes: HistogramWidget['mode'][] = ['structural', 'temporal'];

export const widgetVisualizationTypes: {
  category: Widget['widget_type'];
  seriesLimit: number;
  modes?: HistogramWidget['mode'][];
  fields?: string[];
  steps?: StepType[];
  limit?: boolean;
}[] = [
  {
    category: 'security-coverage',
    seriesLimit: 2,
    modes: ['structural'],
    fields: ['base_attack_patterns_side', 'base_created_at', 'base_updated_at'],
    limit: false,
  },
  {
    category: 'attack-path',
    modes: ['structural'],
    seriesLimit: 2,
    fields: ['base_attack_patterns_side', 'base_created_at', 'base_updated_at'],
    limit: false,
  },
  {
    category: 'vertical-barchart',
    seriesLimit: 5,
  },
  {
    category: 'horizontal-barchart',
    seriesLimit: 2,
    modes: ['structural'],
  },
  {
    category: 'line',
    modes: ['temporal'],
    seriesLimit: 5,
  },
  {
    category: 'donut',
    modes: ['structural'],
    seriesLimit: 1,
  },
  {
    category: 'list',
    seriesLimit: 1,
  },
  {
    category: 'number',
    seriesLimit: 1,
  },
];

export const renderWidgetIcon = (type: Widget['widget_type'], fontSize: 'large' | 'small' | 'medium') => {
  switch (type) {
    case 'vertical-barchart':
      return <ChartBar fontSize={fontSize} color="primary" />;
    case 'horizontal-barchart':
      return <AlignHorizontalLeft fontSize={fontSize} color="primary" />;
    case 'line':
      return <ChartLine fontSize={fontSize} color="primary" />;
    case 'donut':
      return <ChartDonut fontSize={fontSize} color="primary" />;
    case 'security-coverage':
      return <TableChart fontSize={fontSize} color="primary" />;
    case 'attack-path':
      return <AccountTree fontSize={fontSize} color="primary" />;
    case 'list':
      return <List fontSize={fontSize} color="primary" />;
    case 'number':
      return <Counter fontSize={fontSize} color="primary" />;
    default:
      return <div />;
  }
};

export const getCurrentSeriesLimit = (type: Widget['widget_type']) => {
  return widgetVisualizationTypes.find(widget => widget.category === type)?.seriesLimit ?? 0;
};

export const getAvailableModes = (type: Widget['widget_type']) => {
  return widgetVisualizationTypes.find(widget => widget.category === type)?.modes ?? defaultModes;
};

export const getLimit = (type: Widget['widget_type']) => {
  return widgetVisualizationTypes.find(widget => widget.category === type)?.limit ?? true;
};

export const getAvailableSteps = (type: Widget['widget_type']) => {
  return widgetVisualizationTypes.find(widget => widget.category === type)?.steps ?? defaultSteps;
};

export const getAvailableFields = (type: Widget['widget_type']) => {
  return widgetVisualizationTypes.find(widget => widget.category === type)?.fields ?? null;
};

export const getWidgetTitle = (widgetTitle: Widget['widget_config']['title'], type: Widget['widget_type'], t: (key: string) => string) => {
  if (type === 'security-coverage') {
    return !widgetTitle ? t('Security Coverage') : widgetTitle;
  } else if (type === 'attack-path') {
    return !widgetTitle ? t('Attack Path') : widgetTitle;
  }
  return widgetTitle ?? '';
};

export const extractGroupOptionsFromCustomDashboardParameters = (customDashboardParameters: CustomDashboardParameters[] = []) => {
  const groupOptionsMap = new Map<string, GroupOption[]>();
  customDashboardParameters.forEach((p) => {
    if (p.custom_dashboards_parameter_type === 'simulation') {
      const items = groupOptionsMap.get('base_simulation_side') ?? [];
      const option = createGroupOption(p.custom_dashboards_parameter_id, p.custom_dashboards_parameter_name, 'Parameters');
      if (!items.map(i => i.id).includes(option.id)) groupOptionsMap.set('base_simulation_side', [...items, option]);
    }
  });
  return groupOptionsMap;
};

// -- FILTERS --

export const BASE_ENTITY_FILTER_KEY = 'base_entity';
export const getBaseEntities = (filterGroup: FilterGroup | undefined) => {
  return filterGroup?.filters?.filter(f => f.key === BASE_ENTITY_FILTER_KEY).map(f => f.values ?? []).flat();
};
export const excludeBaseEntities = (filterGroup: FilterGroup | undefined) => {
  if (!filterGroup) {
    return undefined;
  }
  return {
    mode: filterGroup.mode,
    filters: filterGroup.filters?.filter(f => f.key !== BASE_ENTITY_FILTER_KEY) ?? [],
  };
};
export const getDefaultValuesForType = (
  currentValues: Map<string, GroupOption[]>,
  param: CustomDashboardParameters,
  key: 'base_simulation_side' | 'base_scenario_side',
) => {
  const values = currentValues;
  const items = values.get(key) ?? [];
  const option = createGroupOption(
    param.custom_dashboards_parameter_id,
    param.custom_dashboards_parameter_name,
    'Parameters',
  );

  if (!items.some(i => i.id === option.id)) {
    values.set(key, [...items, option]);
  }
  return values;
};

// -- MATRIX MITRE --
const entityFilter: Filter = {
  key: BASE_ENTITY_FILTER_KEY,
  mode: 'and',
  operator: 'eq',
  values: ['expectation-inject'],
};
const statusSuccessFilter: Filter = {
  key: 'inject_expectation_status',
  mode: 'and',
  operator: 'eq',
  values: ['SUCCESS'],
};
const statusFailedFilter: Filter = {
  key: 'inject_expectation_status',
  mode: 'and',
  operator: 'eq',
  values: ['FAILED'],
};
const typeFilter: (injectExpectationType: InjectExpectation['inject_expectation_type']) => Filter = injectExpectationType => ({
  key: 'inject_expectation_type',
  mode: 'and',
  operator: 'eq',
  values: [injectExpectationType],
});
const simulationFilter: (simulationId: Exercise['exercise_id']) => Filter = simulationId => ({
  key: 'base_simulation_side',
  mode: 'and',
  operator: 'eq',
  values: [simulationId],
});

const getSuccessSeries: (injectExpectationType: InjectExpectation['inject_expectation_type'], simulationId?: Exercise['exercise_id']) => Series = (injectExpectationType, simulationId) => {
  return {
    filter: {
      mode: 'and',
      filters: [
        entityFilter,
        statusSuccessFilter,
        typeFilter(injectExpectationType),
        ...(simulationId ? [simulationFilter(simulationId)] : []),
      ],
    },
    name: 'SUCCESS',
  };
};

const getFailedSeries: (injectExpectationType: InjectExpectation['inject_expectation_type'], simulationId?: Exercise['exercise_id']) => Series = (injectExpectationType, simulationId) => {
  return {
    filter: {
      mode: 'and',
      filters: [
        entityFilter,
        statusFailedFilter,
        typeFilter(injectExpectationType),
        ...(simulationId ? [simulationFilter(simulationId)] : []),
      ],
    },
    name: 'FAILED',
  };
};

export const getSeries: (injectExpectationType: InjectExpectation['inject_expectation_type'], simulationId?: Exercise['exercise_id']) => Series[] = (injectExpectationType, simulationId) => {
  return [getSuccessSeries(injectExpectationType, simulationId), getFailedSeries(injectExpectationType, simulationId)];
};

export const updateSimulationFilterOnSeries = (series: Series[], simulationId?: Exercise['exercise_id']) => {
  if (!simulationId) {
    return series;
  }
  series.forEach((s) => {
    const filter = s.filter?.filters?.find(f => f.key === 'base_simulation_side');
    if (filter) {
      filter.values = [simulationId];
    } else {
      s.filter?.filters?.push(simulationFilter(simulationId));
    }
  });
  return series;
};
