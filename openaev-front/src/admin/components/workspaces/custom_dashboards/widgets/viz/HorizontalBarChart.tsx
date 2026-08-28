import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import Chart from '../../../../../../components/Chart';
import { useFormatter } from '../../../../../../components/i18n';
import { type StructuralHistogramWidget } from '../../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../../utils/Charts';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { type SerieData } from '../WidgetViz';
import seriesSemanticColors from './chartColorUtils';
import { isApexSeriesEmpty, sampleTtpSeries, toApexSeries } from './sample/sampleData';
import SamplePreview from './sample/SamplePreview';

interface Props {
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
  series: ApexAxisChartSeries;
}

const useStyles = makeStyles()(() => ({ barChartContainer: { '& .apexcharts-bar-area': { cursor: 'pointer' } } }));

const HorizontalBarChart: FunctionComponent<Props> = ({ widgetId, widgetConfig, series: realSeries }) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t, fld } = useFormatter();

  const isSample = isApexSeriesEmpty(realSeries);
  const series = useMemo(
    () => (isSample ? toApexSeries(sampleTtpSeries) : realSeries),
    [isSample, realSeries],
  );

  const { openWidgetResults } = useContext(CustomDashboardContext);

  // Memoize click handler
  const onBarClick = useCallback((_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const dataPoint = series[config.seriesIndex].data[config.dataPointIndex] as SerieData;
    openWidgetResults({
      widgetId,
      filter_values_map: { [widgetConfig.field]: [dataPoint?.meta ?? ''] },
      series_index: config.seriesIndex,
    });
  }, [series, openWidgetResults, widgetId]);

  // Memoize widget mode
  const widgetMode = useMemo((): string => {
    if (widgetConfig.widget_configuration_type === 'temporal-histogram' || widgetConfig.widget_configuration_type === 'structural-histogram') {
      return widgetConfig.mode;
    }
    return 'structural';
  }, [widgetConfig]);

  // Memoize empty chart text
  const emptyChartText = useMemo(() => t('No data to display'), [t]);

  // Single-series structural breakdowns read better with one distinct color
  // per bar; multi-series comparisons keep semantic success/miss colors.
  const distributed = useMemo(() => series.length <= 1, [series]);
  const chartColors = useMemo(
    () => (distributed
      ? undefined
      : seriesSemanticColors(theme, series.map(s => (typeof s.name === 'string' ? s.name : ''))) ?? undefined),
    [theme, series, distributed],
  );

  // Memoize chart options
  const options = useMemo(
    () => horizontalBarsChartOptions({
      theme,
      xFormatter: widgetMode === 'temporal' ? fld : null,
      categories: [],
      legend: !distributed,
      distributed,
      // Grouped multi-series bars (e.g. Detected vs Prevented) sit too close for
      // per-bar end labels — they overlap; single-series keeps them.
      showDataLabels: distributed,
      emptyChartText,
      onBarClick,
      chartColors,
    }),
    [theme, widgetMode, fld, emptyChartText, onBarClick, chartColors, distributed],
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

export default memo(HorizontalBarChart);
