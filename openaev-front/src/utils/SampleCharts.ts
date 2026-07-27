import { type Theme } from '@mui/material/styles';

/**
 * Representative SAMPLE series for the ApexCharts-based distribution charts
 * (simulation overview and injects distribution view).
 *
 * Rendered greyed out inside a `SamplePreview` when a chart has no real data
 * yet, following the same convention as the custom dashboard widgets: the
 * screen always previews what it will look like once the simulation runs,
 * instead of showing a bare "no data" message. Clearly labelled "Sample" in
 * the UI, replaced automatically by live data, never persisted or sent
 * anywhere.
 */

type BarPoint = {
  x: string;
  y: number;
  fillColor: string;
};

/** Horizontal bar sample: one series, one bar per category label. */
export const sampleHorizontalBarSeries = (
  name: string,
  labels: string[],
  theme: Theme,
  values: number[] = [12, 8, 5, 3],
): {
  name: string;
  data: BarPoint[];
}[] => {
  return [{
    name,
    data: labels.map((label, index) => ({
      x: label,
      y: values[index % values.length],
      fillColor: theme.palette.primary.main,
    })),
  }];
};

/** Height matching the real charts' `50 + n * 50` sizing rule. */
export const sampleHorizontalBarHeight = (labels: string[]): number => 50 + labels.length * 50;

const weeklyDate = (weeksAgo: number): string => {
  const date = new Date();
  date.setDate(date.getDate() - weeksAgo * 7);
  return date.toISOString();
};

/** Weekly success-rate (%) sample: one gently rising series over 8 weeks. */
export const sampleSuccessRateSeries = (
  name: string,
  theme: Theme,
): {
  name: string;
  color: string;
  data: {
    x: string;
    y: number;
  }[];
}[] => {
  const values = [42, 48, 55, 53, 61, 68, 72, 78];
  return [{
    name,
    color: theme.palette.primary.main,
    data: values.map((y, pointIndex) => ({
      x: weeklyDate(values.length - 1 - pointIndex),
      y,
    })),
  }];
};

/** Weekly activity-count sample: one gently varying series over 8 weeks. */
export const sampleCountOverTimeSeries = (
  name: string,
  theme: Theme,
): {
  name: string;
  color: string;
  data: {
    x: string;
    y: number;
  }[];
}[] => {
  const values = [2, 5, 3, 8, 6, 11, 9, 14];
  return [{
    name,
    color: theme.palette.primary.main,
    data: values.map((y, pointIndex) => ({
      x: weeklyDate(values.length - 1 - pointIndex),
      y,
    })),
  }];
};

/** Cumulative score-over-time sample: two ascending series over 8 weeks. */
export const sampleScoreOverTimeSeries = (
  names: [string, string],
  theme: Theme,
): {
  name: string;
  color: string;
  data: {
    x: string;
    y: number;
  }[];
}[] => {
  const values: [number[], number[]] = [
    [4, 9, 15, 22, 31, 38, 47, 55],
    [2, 6, 11, 17, 24, 30, 36, 41],
  ];
  const colors = [theme.palette.primary.main, theme.palette.secondary.main];
  return names.map((name, seriesIndex) => ({
    name,
    color: colors[seriesIndex],
    data: values[seriesIndex].map((y, pointIndex) => ({
      x: weeklyDate(values[seriesIndex].length - 1 - pointIndex),
      y,
    })),
  }));
};
