import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import Chart from '../../../../../../components/Chart';
import { useFormatter } from '../../../../../../components/i18n';
import type { DateHistogramWidget, StructuralHistogramWidget, Widget } from '../../../../../../utils/api-types';
import { verticalBarsChartOptions } from '../../../../../../utils/Charts';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { type SerieData } from '../WidgetViz';
import seriesSemanticColors from './chartColorUtils';
import { isApexSeriesEmpty, samplePlatformsSeries, sampleTemporalSeries, toApexSeries } from './sample/sampleData';
import SamplePreview from './sample/SamplePreview';

interface Props {
  widgetId: string;
  widgetConfig: Widget['widget_config'];
  series: ApexAxisChartSeries;
  errorMessage: string;
}

const useStyles = makeStyles()(() => ({ barChartContainer: { '& .apexcharts-bar-area': { cursor: 'pointer' } } }));

const VerticalBarChart: FunctionComponent<Props> = ({ widgetId, widgetConfig, series: realSeries, errorMessage }) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t, fld } = useFormatter();

  // Memoize widget mode
  const widgetMode = useMemo((): string => {
    if (widgetConfig.widget_configuration_type === 'temporal-histogram' || widgetConfig.widget_configuration_type === 'structural-histogram') {
      return (widgetConfig as DateHistogramWidget | StructuralHistogramWidget).mode;
    }
    return 'structural';
  }, [widgetConfig]);

  const isSample = errorMessage.length === 0 && isApexSeriesEmpty(realSeries);
  const series = useMemo(() => {
    if (!isSample) return realSeries;
    const sample = widgetMode === 'temporal'
      ? sampleTemporalSeries(
          String(realSeries[0]?.name ?? 'Series 1'),
          realSeries.length > 1 ? String(realSeries[1]?.name ?? 'Series 2') : undefined,
        )
      : samplePlatformsSeries;
    return toApexSeries(sample);
  }, [isSample, realSeries, widgetMode]);

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  // Memoize click handler
  const onBarClick = useCallback((_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const dataPoint = series[config.seriesIndex].data[config.dataPointIndex] as SerieData;
    const filterKey = widgetConfig.widget_configuration_type === 'temporal-histogram'
      ? 'date'
      : (widgetConfig as StructuralHistogramWidget).field;

    openWidgetDataDrawer({
      widgetId,
      filter_values_map: { [filterKey]: [dataPoint?.meta ?? ''] },
      series_index: config.seriesIndex,
    });
  }, [series, openWidgetDataDrawer, widgetId]);

  // Memoize empty chart text
  const emptyChartText = useMemo(
    () => errorMessage.length > 0 ? errorMessage : t('No data to display'),
    [errorMessage, t],
  );

  // Semantic series colors (successes green, misses red)
  const chartColors = useMemo(
    () => seriesSemanticColors(theme, series.map(s => (typeof s.name === 'string' ? s.name : ''))) ?? undefined,
    [theme, series],
  );

  // Memoize chart options
  const options = useMemo(
    () => verticalBarsChartOptions({
      theme,
      xFormatter: widgetMode === 'temporal' ? fld : null,
      isTimeSeries: widgetMode === 'temporal',
      legend: true,
      tickAmount: 'dataPoints',
      isResult: true,
      emptyChartText,
      onBarClick,
      chartColors,
    }),
    [theme, widgetMode, fld, emptyChartText, onBarClick, chartColors],
  );

  return (
    <SamplePreview active={isSample}>
      <Chart
        options={options}
        series={series}
        type="bar"
        width="100%"
        height="100%"
        className={classes.barChartContainer}
      />
    </SamplePreview>
  );
};

export default memo(VerticalBarChart);
