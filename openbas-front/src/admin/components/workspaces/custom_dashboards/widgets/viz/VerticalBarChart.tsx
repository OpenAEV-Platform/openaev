import { useTheme } from '@mui/material/styles';
import { type ApexOptions } from 'apexcharts';
import moment from 'moment-timezone';
import { type FunctionComponent, useState } from 'react';
import Chart from 'react-apexcharts';

import { createCustomDashboardWidget, deleteCustomDashboardWidget } from '../../../../../../actions/custom_dashboards/customdashboardwidget-action';
import { entities, entitiesWithNoWidget } from '../../../../../../actions/dashboards/dashboard-action';
import Drawer from '../../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../../components/i18n';
import { type EsBase, type Filter, type ListPerspective } from '../../../../../../utils/api-types';
import type { ListConfiguration, Widget, WidgetInput } from '../../../../../../utils/api-types-custom';
import { verticalBarsChartOptions } from '../../../../../../utils/Charts';
import { calcEndDate } from '../../../../../../utils/Time';
import { type ParameterOption } from '../../CustomDashboardContext';
import ListWidget from './list/ListWidget';

interface Props {
  widgetConfig: Widget['widget_config'];
  series: ApexAxisChartSeries;
  errorMessage: string;
  customDashboardParameters?: Record<string, ParameterOption>;
  widgetCustomDashboard?: string;
}

const VerticalBarChart: FunctionComponent<Props> = ({ widgetConfig, series, errorMessage, customDashboardParameters, widgetCustomDashboard }) => {
  const theme = useTheme();
  const { t, fld } = useFormatter();

  const widgetMode = (): 'structural' | 'temporal' => {
    if (widgetConfig.widget_configuration_type === 'temporal-histogram' || widgetConfig.widget_configuration_type === 'structural-histogram') {
      return widgetConfig.mode;
    }
    return 'structural';
  };

  const [open, setOpen] = useState(false);
  const [entitiesVizData, setEntitiesVizData] = useState<EsBase[]>([]);
  const [listWidgetConfig, setListWidgetConfig] = useState<ListConfiguration>();

  const widgetValue = (): string => {
    if (widgetConfig.widget_configuration_type === 'temporal-histogram' || widgetConfig.widget_configuration_type === 'structural-histogram') {
      const filter: Filter[] = widgetConfig.series[0].filter.filters.filter(f => f.key === 'base_entity');
      return filter[0].values[0];
    }
    return 'value';
  };

  function onBarClickEvent(event: any, charContext: any, config: any) {
    const dataPointIndex: number = config.dataPointIndex;
    const serie = series[0].data[dataPointIndex];
    let columns: string[] = [];
    let start: string = '';
    let end: string = '';
    const filters: Filter[] = [];

    if (widgetValue() === 'endpoint') {
      columns = ['endpoint_name', 'endpoint_ips', 'endpoint_platform'];
    } else if (widgetValue() === 'vulnerable-endpoint') {
      columns = ['vulnerable_endpoint_hostname', 'vulnerable_endpoint_action', 'vulnerable_endpoint_findings_summary'];
    }

    filters.push({
      key: 'base_entity',
      mode: 'or',
      values: [widgetValue()],
      operator: 'eq',
    });

    if (serie !== null && typeof serie === 'object' && 'x' in serie) {
      if (widgetConfig.widget_configuration_type === 'temporal-histogram') {
        start = serie.x;
        end = calcEndDate(start, widgetConfig.interval).toISOString();
      } else if (widgetConfig.widget_configuration_type === 'structural-histogram') {
        filters.push({
          key: widgetConfig.field,
          mode: 'or',
          values: [serie.x],
          operator: 'eq',
        });
      }
    }

    const listPerspective: ListPerspective = {
      name: '',
      filter: {
        mode: 'and',
        filters: filters,
      },
    };

    const listConfig: ListConfiguration = {
      perspective: listPerspective,
      columns: columns,
      sorts: [
        {
          fieldName: 'base_created_at',
          direction: 'DESC',
        },
      ],
      time_range: start.length > 0 ? 'CUSTOM' : widgetConfig.time_range,
      start: start,
      end: end,
      date_attribute: widgetConfig.date_attribute,
      limit: 100,
      widget_configuration_type: 'list',
    };
    setListWidgetConfig(listConfig);

    const params: Record<string, string> = Object.fromEntries(
      Object.entries(customDashboardParameters).map(([key, val]) => [key, val.value]),
    );

    entitiesWithNoWidget(widgetCustomDashboard, params, listConfig).then((response) => {
      setEntitiesVizData(response.data);
    });

    setOpen(true);
  }

  function onBarCursorEvent(event: any, charContext: any, config: any) {
    event.target.style.cursor = 'pointer';
  }

  return (
    <>
      <Chart
        options={verticalBarsChartOptions(
          theme,
          widgetMode() === 'temporal' ? fld : null,
          undefined,
          false,
          widgetMode() === 'temporal',
          false,
          true,
          'dataPoints',
          true,
          false,
          undefined,
          errorMessage.length > 0 ? errorMessage : t('No data to display'),
          undefined,
          onBarClickEvent,
          onBarCursorEvent,
        )}
        series={series}
        type="bar"
        width="100%"
        height="100%"
      />

      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Display list')}
      >
        <ListWidget config={listWidgetConfig} elements={entitiesVizData} />
      </Drawer>
    </>

  );
};

export default VerticalBarChart;
