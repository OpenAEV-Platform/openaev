import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';
import Chart from 'react-apexcharts';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import type { Widget } from '../../../../../../utils/api-types-custom';
import { verticalBarsChartOptions } from '../../../../../../utils/Charts';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { type SerieData } from '../WidgetViz';

interface Props {
  widgetId: string;
  widgetConfig: Widget['widget_config'];
  series: ApexAxisChartSeries;
  errorMessage: string;
}

const useStyles = makeStyles()(() => ({ barChartContainer: { '& .apexcharts-bar-area': { cursor: 'pointer' } } }));

const VerticalBarChart: FunctionComponent<Props> = ({ widgetId, widgetConfig, series, errorMessage }) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t, fld } = useFormatter();

  const widgetMode = (): 'structural' | 'temporal' => {
    if (widgetConfig.widget_configuration_type === 'temporal-histogram' || widgetConfig.widget_configuration_type === 'structural-histogram') {
      return widgetConfig.mode;
    }
    return 'structural';
  };

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  const onBarClick = (_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const dataPoint = series[config.seriesIndex].data[config.dataPointIndex] as SerieData;
    openWidgetDataDrawer({
      widgetId,
      filter_values: [dataPoint?.meta ?? ''],
      series_index: config.seriesIndex,
    });
  };

  return (
    <Chart
      options={verticalBarsChartOptions({
        theme,
        xFormatter: widgetMode() === 'temporal' ? fld : null,
        isTimeSeries: widgetMode() === 'temporal',
        legend: true,
        tickAmount: 'dataPoints',
        isResult: true,
        emptyChartText: errorMessage.length > 0 ? errorMessage : t('No data to display'),
        onBarClick,
      })}
      series={series}
      type="bar"
      width="100%"
      height="100%"
      className={classes.barChartContainer}
    />
  );
};

export default VerticalBarChart;
