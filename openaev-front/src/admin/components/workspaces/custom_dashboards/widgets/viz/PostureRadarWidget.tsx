import { useTheme } from '@mui/material/styles';
import { type ApexOptions } from 'apexcharts';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';

import Chart from '../../../../../../components/Chart';
import { useFormatter } from '../../../../../../components/i18n';
import { type EsSeries, type StructuralHistogramWidget } from '../../../../../../utils/api-types';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { isSeriesEmpty, sampleRadarSeries } from './sample/sampleData';
import SamplePreview from './sample/SamplePreview';

interface Props {
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
  series: EsSeries[];
}

/**
 * Security posture radar: overlays every series of a structural histogram on a
 * radar chart (e.g. SUCCESS vs FAILED expectations per security platform),
 * exposing coverage strengths and blind spots at a glance.
 */
const PostureRadarWidget: FunctionComponent<Props> = ({ widgetId, widgetConfig, series }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { openWidgetResults } = useContext(CustomDashboardContext);

  const isSample = isSeriesEmpty(series);
  const displaySeries = isSample ? sampleRadarSeries : series;

  // Align every series on the union of bucket labels
  const labels = useMemo(() => {
    const all: string[] = [];
    displaySeries.forEach((s) => {
      (s.data ?? []).forEach((d) => {
        if (d.label && !all.includes(d.label)) all.push(d.label);
      });
    });
    return all;
  }, [displaySeries]);

  // Bucket keys aligned on `labels` (the key carries the entity id used for filtering)
  const labelKeys = useMemo(
    () => labels.map((label) => {
      for (const s of displaySeries) {
        const found = (s.data ?? []).find(d => d.label === label);
        if (found?.key) return found.key;
      }
      return label;
    }),
    [labels, displaySeries],
  );

  // Clicking a vertex drills into the entities behind that bucket
  const onDataPointClick = useCallback((_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const key = labelKeys[config.dataPointIndex];
    if (!key) return;
    openWidgetResults({
      widgetId,
      filter_values_map: { [widgetConfig.field]: [key] },
      series_index: config.seriesIndex,
    });
  }, [labelKeys, openWidgetResults, widgetId, widgetConfig.field]);

  const chartSeries = useMemo(
    () => displaySeries.map(s => ({
      name: s.label ? t(s.label) : '',
      data: labels.map(label => (s.data ?? []).find(d => d.label === label)?.value ?? 0),
    })),
    [displaySeries, labels, t],
  );

  const seriesColors = useMemo(() => {
    const pickColor = (label: string, index: number) => {
      const upper = label.toUpperCase();
      if (upper.includes('SUCCESS') || upper.includes('DETECTED') || upper.includes('PREVENTED')) {
        return theme.palette.success.main;
      }
      if (upper.includes('FAIL') || upper.includes('NOT ')) {
        return theme.palette.error.main;
      }
      return [theme.palette.primary.main, theme.palette.secondary.main, theme.palette.warning.main][index % 3];
    };
    return displaySeries.map((s, i) => pickColor(s.label ?? '', i));
  }, [displaySeries, theme]);

  const options: ApexOptions = useMemo(() => ({
    chart: {
      type: 'radar',
      background: 'transparent',
      toolbar: { show: false },
      parentHeightOffset: 0,
      offsetY: 0,
      animations: {
        enabled: true,
        speed: 900,
      },
      events: {
        dataPointSelection(event, _, config?) {
          onDataPointClick(event, config);
        },
      },
    },
    theme: { mode: theme.palette.mode },
    colors: seriesColors,
    // Axis labels are entity names (user data), never translation keys.
    labels,
    dataLabels: { enabled: false },
    stroke: { width: 2 },
    fill: { opacity: 0.18 },
    markers: {
      size: 3,
      strokeWidth: 1,
      hover: { size: 6 },
    },
    legend: {
      show: true,
      position: 'bottom',
      offsetY: 4,
      fontFamily: '"IBM Plex Sans", sans-serif',
      markers: { size: 5 },
      itemMargin: {
        horizontal: 10,
        vertical: 0,
      },
    },
    tooltip: { theme: theme.palette.mode },
    xaxis: {
      labels: {
        style: {
          fontSize: '10px',
          fontFamily: '"IBM Plex Sans", sans-serif',
          colors: labels.map(() => theme.palette.text.secondary),
        },
      },
    },
    yaxis: { show: false },
    plotOptions: {
      radar: {
        size: 108,
        polygons: {
          strokeColors: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, .08)' : 'rgba(0, 0, 0, .08)',
          connectorColors: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, .08)' : 'rgba(0, 0, 0, .08)',
          fill: { colors: ['transparent'] },
        },
      },
    },
  }), [theme, labels, seriesColors, t, onDataPointClick]);

  return (
    <SamplePreview active={isSample} variant="subtle">
      <Chart
        key={`radar-${widgetId}-${isSample}`}
        options={options}
        series={chartSeries}
        type="radar"
        width="100%"
        height="100%"
      />
    </SamplePreview>
  );
};

export default memo(PostureRadarWidget);
