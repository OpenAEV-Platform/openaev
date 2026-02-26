import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';
import Chart from 'react-apexcharts';

import { useFormatter } from '../../../../../../components/i18n';
import { lineChartOptions } from '../../../../../../utils/Charts';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { type SerieData } from '../WidgetViz';

interface Props {
  widgetId: string;
  series: ApexAxisChartSeries;
}

const LineChart: FunctionComponent<Props> = ({ widgetId, series }) => {
  const theme = useTheme();
  const { t, fld } = useFormatter();

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

  // Memoize chart options
  const options = useMemo(
    () => lineChartOptions({
      theme,
      isTimeSeries: true,
      xFormatter: fld,
      distributed,
      emptyChartText,
      onDataPointClick,
    }),
    [theme, fld, distributed, emptyChartText, onDataPointClick],
  );

  return (
    <Chart
      options={options}
      series={series}
      type="line"
      width="100%"
      height="100%"
    />
  );
};

export default memo(LineChart);
