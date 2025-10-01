import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';
import Chart from 'react-apexcharts';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import type { Widget } from '../../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../../utils/Charts';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { type SerieData } from '../WidgetViz';

interface Props {
  widgetId: string;
  widgetConfig: Widget['widget_config'];
  series: ApexAxisChartSeries;
}

const useStyles = makeStyles()(() => ({ barChartContainer: { '& .apexcharts-bar-area': { cursor: 'pointer' } } }));

const HorizontalBarChart: FunctionComponent<Props> = ({ widgetId, widgetConfig, series }) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t, fld } = useFormatter();

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

  const widgetMode = (): 'structural' | 'temporal' => {
    if (widgetConfig.widget_configuration_type === 'temporal-histogram' || widgetConfig.widget_configuration_type === 'structural-histogram') {
      return widgetConfig.mode;
    }
    return 'structural';
  };

  return (
    <Chart
      options={horizontalBarsChartOptions({
        theme,
        xFormatter: widgetMode() === 'temporal' ? fld : null,
        categories: [],
        legend: true,
        emptyChartText: t('No data to display'),
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

export default HorizontalBarChart;
