import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';

import Chart from '../../../../../../components/Chart';
import { useFormatter } from '../../../../../../components/i18n';
import { lineChartOptions } from '../../../../../../utils/Charts';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { type SerieData } from '../WidgetViz';
import { isApexSeriesEmpty, sampleTemporalSeries, toApexSeries } from './sample/sampleData';
import SamplePreview from './sample/SamplePreview';

interface Props {
  widgetId: string;
  series: ApexAxisChartSeries;
}

const LineChart: FunctionComponent<Props> = ({ widgetId, series: realSeries }) => {
  const theme = useTheme();
  const { t, fld } = useFormatter();

  const isSample = isApexSeriesEmpty(realSeries);
  const series = useMemo(() => {
    if (!isSample) return realSeries;
    return toApexSeries(sampleTemporalSeries(
      String(realSeries?.[0]?.name ?? 'Series 1'),
      (realSeries?.length ?? 0) > 1 ? String(realSeries?.[1]?.name ?? 'Series 2') : undefined,
    ));
  }, [isSample, realSeries]);

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  // Memoize click handler
  const onDataPointClick = useCallback((_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    if (!series) {
      return;
    }
    const dataPointIndex = series[config.seriesIndex].data[config.dataPointIndex] as SerieData;
    if (!dataPointIndex || Number(dataPointIndex.y) === 0) {
      return;
    }

    openWidgetDataDrawer({
      widgetId,
      filter_values_map: { date: [dataPointIndex?.x ?? ''] },
      series_index: config.seriesIndex,
    });
  }, [series, openWidgetDataDrawer, widgetId]);

  // Memoize distributed flag
  const distributed = useMemo(
    () => series ? series.length > 1 : false,
    [series],
  );

  const emptyChartText = useMemo(() => t('No data to display'), [t]);

  // Rendered as a smooth gradient area: the line stays the hero while the
  // soft fill anchors the trend visually on the dark background.
  const options = useMemo(
    () => ({
      ...lineChartOptions({
        theme,
        isTimeSeries: true,
        xFormatter: fld,
        distributed,
        emptyChartText,
        onDataPointClick,
      }),
      stroke: {
        curve: 'smooth' as const,
        width: 2,
      },
      fill: {
        type: 'gradient',
        gradient: {
          shadeIntensity: 1,
          opacityFrom: 0.35,
          opacityTo: 0.02,
          stops: [0, 95],
        },
      },
    }),
    [theme, fld, distributed, emptyChartText, onDataPointClick],
  );

  return (
    <SamplePreview active={isSample}>
      <Chart
        options={options}
        series={series}
        type="area"
        width="100%"
        height="100%"
      />
    </SamplePreview>
  );
};

export default memo(LineChart);
