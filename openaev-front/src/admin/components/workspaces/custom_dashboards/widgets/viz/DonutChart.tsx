import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';
import Chart from 'react-apexcharts';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import { type StructuralHistogramWidget } from '../../../../../../utils/api-types';
import { donutChartOptions } from '../../../../../../utils/Charts';
import { getStatusColor } from '../../../../../../utils/statusUtils';
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

  // Memoize click handler
  const onClick = useCallback((_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const dataPoint = datas[config.dataPointIndex];
    openWidgetDataDrawer({
      widgetId,
      filter_values: [dataPoint?.meta ?? ''],
      series_index: config.seriesIndex,
    });
  }, [datas, openWidgetDataDrawer, widgetId]);

  // Memoize labels
  const labels = useMemo(
    () => datas.map(s => s?.x ?? t('-')),
    [datas, t],
  );

  // Memoize chart colors
  const chartColors = useMemo(() => {
    const isStatusBreakdown = 'field' in widgetConfig
      && (widgetConfig.field.toLowerCase().includes('status')
        || widgetConfig.field.toLowerCase().includes('vulnerable_endpoint_action'));
    return isStatusBreakdown ? labels.map(label => getStatusColor(theme, label)) : [];
  }, [widgetConfig, labels, theme]);

  // Memoize chart options
  const options = useMemo(
    () => donutChartOptions({
      theme,
      labels,
      chartColors,
      onClick,
    }),
    [theme, labels, chartColors, onClick],
  );

  // Memoize series data
  const series = useMemo(
    () => datas.map(s => s?.y ?? 0),
    [datas],
  );

  return (
    <Chart
      options={options}
      series={series}
      type="donut"
      width="100%"
      height="100%"
      className={classes.chartContainer}
    />
  );
};

export default memo(DonutChart);
