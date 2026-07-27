import { alpha, type Theme } from '@mui/material/styles';
import { type ApexOptions } from 'apexcharts';

/**
 * Shared ApexCharts options for the "over time" area trends of asset-side
 * detail pages (posture score / injects played). One builder so the security
 * platform, asset and asset group charts stay pixel-identical.
 */
const buildTrendAreaOptions = ({ theme, formatDate, noDataText, percent, color, singlePoint }: {
  theme: Theme;
  /** Date formatter for x-axis labels and tooltips (nsdt from useFormatter). */
  formatDate: (value: string) => string;
  noDataText: string;
  /** Percentage scale (0-100 with % labels) vs raw count scale. */
  percent: boolean;
  color: string;
  /** Show dots when the series has a single bucket (a lone point is invisible otherwise). */
  singlePoint: boolean;
}): ApexOptions => ({
  chart: {
    type: 'area',
    background: 'transparent',
    toolbar: { show: false },
    zoom: { enabled: false },
    foreColor: theme.palette.text.secondary,
    fontFamily: '"IBM Plex Sans", sans-serif',
    parentHeightOffset: 0,
  },
  theme: { mode: theme.palette.mode },
  colors: [color],
  dataLabels: { enabled: false },
  stroke: {
    curve: 'smooth',
    width: 2.5,
    lineCap: 'round',
  },
  fill: {
    type: 'gradient',
    gradient: {
      shadeIntensity: 1,
      opacityFrom: 0.3,
      opacityTo: 0,
      stops: [0, 95],
    },
  },
  markers: {
    size: singlePoint ? 5 : 0,
    strokeWidth: 2,
    strokeColors: theme.palette.background.paper,
    hover: { size: 6 },
  },
  grid: {
    borderColor: alpha(theme.palette.text.primary, 0.08),
    strokeDashArray: 4,
    xaxis: { lines: { show: false } },
    yaxis: { lines: { show: true } },
  },
  xaxis: {
    type: 'category',
    tickPlacement: 'on',
    axisBorder: { show: false },
    axisTicks: { show: false },
    labels: {
      rotate: 0,
      hideOverlappingLabels: true,
      formatter: (value: string) => (value ? formatDate(value) : value),
      style: { fontSize: '11px' },
    },
    tooltip: { enabled: false },
  },
  yaxis: percent
    ? {
        min: 0,
        max: 100,
        tickAmount: 5,
        labels: {
          formatter: (value: number) => `${Math.round(value)}%`,
          style: { fontSize: '11px' },
        },
      }
    : {
        min: 0,
        forceNiceScale: true,
        labels: {
          formatter: (value: number) => `${Math.round(value)}`,
          style: { fontSize: '11px' },
        },
      },
  tooltip: {
    theme: theme.palette.mode,
    x: { formatter: (value: number | string) => (value ? formatDate(String(value)) : String(value)) },
    y: { formatter: (value: number) => (percent ? `${Math.round(value)}%` : `${Math.round(value)}`) },
  },
  noData: { text: noDataText },
});

export default buildTrendAreaOptions;
