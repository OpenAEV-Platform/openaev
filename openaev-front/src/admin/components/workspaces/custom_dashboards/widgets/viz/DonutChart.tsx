import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import Chart from '../../../../../../components/Chart';
import { useFormatter } from '../../../../../../components/i18n';
import { type StructuralHistogramWidget } from '../../../../../../utils/api-types';
import { donutChartOptions } from '../../../../../../utils/Charts';
import { getStatusColor } from '../../../../../../utils/statusUtils';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { sampleStatusSeries } from './sample/sampleData';
import SamplePreview from './sample/SamplePreview';

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

const DonutChart: FunctionComponent<Props> = ({ widgetId, widgetConfig, datas: realDatas }: Props) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();

  const isSample = realDatas.length === 0 || realDatas.every(d => (d.y ?? 0) === 0);
  const datas = useMemo(() => (isSample
    ? (sampleStatusSeries[0].data ?? []).map(d => ({
        x: d.label,
        y: d.value,
        meta: d.key,
      }))
    : realDatas), [isSample, realDatas]);

  const { openWidgetResults } = useContext(CustomDashboardContext);

  // Memoize click handler
  const onClick = useCallback((_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const dataPoint = datas[config.dataPointIndex];
    openWidgetResults({
      widgetId,
      filter_values_map: { [widgetConfig.field]: [dataPoint?.meta ?? ''] },
      series_index: config.seriesIndex,
    });
  }, [datas, openWidgetResults, widgetId]);

  // Memoize labels
  const labels = useMemo(
    () => datas.map(s => s?.x ?? t('-')),
    [datas, t],
  );

  // Memoize chart colors
  const chartColors = useMemo(() => {
    const isStatusBreakdown = isSample
      || ('field' in widgetConfig
        && (widgetConfig.field.toLowerCase().includes('status')
          || widgetConfig.field.toLowerCase().includes('vulnerable_endpoint_action')));
    return isStatusBreakdown ? labels.map(label => getStatusColor(theme, label)) : [];
  }, [widgetConfig, labels, theme, isSample]);

  // Memoize empty chart text
  const emptyChartText = useMemo(() => t('No data to display'), [t]);

  // Memoize chart options
  const options = useMemo(
    () => donutChartOptions({
      theme,
      labels,
      chartColors,
      emptyChartText,
      onClick,
    }),
    [theme, labels, chartColors, emptyChartText, onClick],
  );

  // Memoize series data
  const series = useMemo(
    () => datas.map(s => s?.y ?? 0),
    [datas],
  );

  return (
    <SamplePreview active={isSample}>
      <Chart
        options={options}
        series={series}
        type="donut"
        width="100%"
        height="100%"
        className={classes.chartContainer}
      />
    </SamplePreview>
  );
};

export default memo(DonutChart);
