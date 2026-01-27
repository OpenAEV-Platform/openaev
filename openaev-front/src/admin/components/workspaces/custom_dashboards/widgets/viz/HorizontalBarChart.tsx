import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';
import Chart from 'react-apexcharts';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import { type DateHistogramWidget, type StructuralHistogramWidget, type Widget } from '../../../../../../utils/api-types';
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

  // Memoize click handler
  const onBarClick = useCallback((_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const dataPoint = series[config.seriesIndex].data[config.dataPointIndex] as SerieData;
    openWidgetDataDrawer({
      widgetId,
      filter_values: [dataPoint?.meta ?? ''],
      series_index: config.seriesIndex,
    });
  }, [series, openWidgetDataDrawer, widgetId]);

  // Memoize widget mode
  const widgetMode = useMemo((): string => {
    if (widgetConfig.widget_configuration_type === 'temporal-histogram' || widgetConfig.widget_configuration_type === 'structural-histogram') {
      return (widgetConfig as DateHistogramWidget | StructuralHistogramWidget).mode;
    }
    return 'structural';
  }, [widgetConfig]);

  // Memoize empty chart text
  const emptyChartText = useMemo(() => t('No data to display'), [t]);

  // Memoize chart options
  const options = useMemo(
    () => horizontalBarsChartOptions({
      theme,
      xFormatter: widgetMode === 'temporal' ? fld : null,
      categories: [],
      legend: true,
      emptyChartText,
      onBarClick,
    }),
    [theme, widgetMode, fld, emptyChartText, onBarClick],
  );

  return (
    <Chart
      options={options}
      series={series}
      type="bar"
      width="100%"
      height="100%"
      className={classes.barChartContainer}
    />
  );
};

export default memo(HorizontalBarChart);
