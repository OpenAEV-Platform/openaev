import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useEffect, useMemo, useRef } from 'react';
import Chart from 'react-apexcharts';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import { type StructuralHistogramWidget } from '../../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../../utils/Charts';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { type SerieData } from '../WidgetViz';

interface Props {
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
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
      filter_values_map: { [widgetConfig.field]: [dataPoint?.meta ?? ''] },
      series_index: config.seriesIndex,
    });
  }, [series, openWidgetDataDrawer, widgetId]);

  // Memoize widget mode
  const widgetMode = useMemo((): string => {
    if (widgetConfig.widget_configuration_type === 'temporal-histogram' || widgetConfig.widget_configuration_type === 'structural-histogram') {
      return widgetConfig.mode;
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
      enableAnimations: false,
    }),
    [theme, widgetMode, fld, emptyChartText, onBarClick],
  );

  const chartRef = useRef<any>(null);
  useEffect(() => {
    return () => {
      if (chartRef.current?.chart) {
        chartRef.current.chart.destroy();
      }
    };
  }, []);

  return (
    <Chart
      ref={chartRef}
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
