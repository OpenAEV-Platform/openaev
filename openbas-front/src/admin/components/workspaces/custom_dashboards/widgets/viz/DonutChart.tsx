import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';
import Chart from 'react-apexcharts';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import { type StructuralHistogramWidget } from '../../../../../../utils/api-types';
import { donutChartOptions } from '../../../../../../utils/Charts';
import { getStatusColor } from '../../../../../../utils/statusColors';
import { CustomDashboardContext } from '../../CustomDashboardContext';

interface Props {
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
  datas: {
    x: string | undefined;
    y: number | undefined;
    meta: string | undefined;
  }[];
}

const useStyles = makeStyles()(() => ({ chartContainer: { '& .apexcharts-pie-area': { cursor: 'pointer' } } }));

const DonutChart: FunctionComponent<Props> = ({ widgetId, widgetConfig, datas }: Props) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  const onClick = (_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const dataPoint = datas[config.dataPointIndex];

    openWidgetDataDrawer({
      widgetId,
      filter_values: [dataPoint?.meta ?? ''],
      series_index: config.seriesIndex,
    });
  };

  const labels = datas.map(s => s?.x ?? t('-'));
  // Apply custom color mapping only when the widget field represents a status breakdown
  const isStatusBreakdown
      = 'field' in widgetConfig && (widgetConfig.field.toLowerCase().includes('status') || widgetConfig.field.toLowerCase().includes('vulnerable_endpoint_action'));
  const chartColors = isStatusBreakdown
    ? labels.map(label => getStatusColor(theme, label))
    : [];

  return (
    <Chart
      options={donutChartOptions({
        theme,
        labels,
        chartColors,
        onClick,
      })}
      series={datas.map(s => s?.y ?? 0)}
      type="donut"
      width="100%"
      height="100%"
      className={classes.chartContainer}
    />
  );
};

export default DonutChart;
