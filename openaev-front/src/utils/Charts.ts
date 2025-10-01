import { type Theme } from '@mui/material';
import * as C from '@mui/material/colors';
import { type ApexOptions } from 'apexcharts';

import { scaleFactor } from '../components/AppThemeProvider';

type Temp = 100 | 200 | 300 | 400 | 500 | 600 | 700 | 800;

const spacing = scaleFactor;
const spacingDot5 = scaleFactor / 2;

export const colors = (temp: Temp): string[] => {
  const tempPlus100 = (temp + 100) as Temp;
  return [
    C.red[temp],
    C.pink[temp],
    C.purple[temp],
    C.deepPurple[temp],
    C.indigo[temp],
    C.blue[temp],
    C.lightBlue[temp],
    C.cyan[temp],
    C.teal[temp],
    C.green[temp],
    C.lightGreen[temp],
    C.lime[temp],
    C.yellow[temp],
    C.amber[temp],
    C.orange[temp],
    C.deepOrange[temp],
    C.brown[temp],
    C.grey[temp],
    C.blueGrey[temp],
    C.red[tempPlus100],
    C.pink[tempPlus100],
    C.purple[tempPlus100],
    C.deepPurple[tempPlus100],
    C.indigo[tempPlus100],
    C.blue[tempPlus100],
    C.lightBlue[tempPlus100],
    C.cyan[tempPlus100],
    C.teal[tempPlus100],
    C.green[tempPlus100],
    C.lightGreen[tempPlus100],
    C.lime[tempPlus100],
    C.yellow[tempPlus100],
    C.amber[tempPlus100],
    C.orange[tempPlus100],
    C.deepOrange[tempPlus100],
    C.brown[tempPlus100],
    C.grey[tempPlus100],
    C.blueGrey[tempPlus100],
  ];
};

/**
 * A custom tooltip for ApexChart.
 * This tooltip only display the label of the data it hovers.
 *
 * Why custom tooltip? To manage text color of the tooltip that cannot be done by
 * the ApexChart API by default.
 *
 * @param {Theme} theme
 */
const simpleLabelTooltip = (theme: Theme): ApexTooltip['custom'] => ({ seriesIndex, w }) => (`
  <div style="background: ${theme.palette.background.nav}; color: ${theme.palette.text?.primary}; padding: 2px 6px; font-size: 12px">
    ${w.config.labels[seriesIndex]}
  </div>
`);

export const resultColors = (temp: Temp) => [
  C.deepPurple[temp],
  C.indigo[temp],
  C.lightBlue[temp],
];

const toolbarOptions = {
  show: false,
  export: {
    csv: {
      columnDelimiter: ',',
      headerCategory: 'category',
      headerValue: 'value',
      dateFormatter(timestamp: number) {
        return new Date(timestamp).toDateString();
      },
    },
  },
};

interface LineChartOption {
  theme: Theme;
  isTimeSeries: boolean;
  xFormatter?: (value: string) => string | string[];
  yFormatter?: (val: number) => (string | string[]);
  tickAmount?: number | 'dataPoints';
  distributed?: boolean;
  dataLabels?: boolean;
  emptyChartText?: string;
  onDataPointClick?: onBarClickFunction;
}
export const lineChartOptions = ({
  theme,
  isTimeSeries = false,
  xFormatter,
  yFormatter,
  tickAmount,
  distributed = false,
  dataLabels = false,
  emptyChartText = '',
  onDataPointClick,
}: LineChartOption): ApexOptions => ({
  chart: {
    type: 'line',
    background: 'transparent',
    toolbar: { show: false },
    foreColor: theme.palette.text?.secondary,
    events: {
      markerClick: function (event, _, config) {
        onDataPointClick?.(event, config);
      },
    },
  },
  theme: { mode: theme.palette.mode },
  dataLabels: { enabled: dataLabels },
  colors: distributed
    ? colors(theme.palette.mode === 'dark' ? 400 : 600)
    : [theme.palette.primary.main],
  states: { hover: { filter: { type: 'lighten' } } },
  grid: {
    borderColor:
      theme.palette.mode === 'dark'
        ? 'rgba(255, 255, 255, .1)'
        : 'rgba(0, 0, 0, .1)',
    strokeDashArray: 3,
  },
  legend: {
    show: true,
    itemMargin: {
      vertical: spacing,
      horizontal: spacingDot5,
    },
  },
  stroke: { curve: 'smooth' },
  markers: {
    size: 4,
    strokeWidth: 2,
    shape: 'circle',
    hover: {
      size: 6,
      sizeOffset: 3,
    },
  },
  tooltip: { theme: theme.palette.mode },
  noData: { text: emptyChartText || 'No data to display' },
  xaxis: {
    type: isTimeSeries ? 'datetime' : 'category',
    tickAmount,
    tickPlacement: 'on',
    labels: {
      formatter: (value: string) => (xFormatter ? xFormatter(value) : value),
      style: {
        fontSize: '12px',
        fontFamily: '"IBM Plex Sans", sans-serif',
      },
    },
    axisBorder: { show: false },
  },
  yaxis: {
    labels: {
      formatter: (value: number) => (yFormatter ? yFormatter(value) : value.toString()),
      style: {
        fontSize: '14px',
        fontFamily: '"IBM Plex Sans", sans-serif',
      },
    },
    axisBorder: { show: false },
  },
});

export const areaChartOptions = (
  theme: Theme,
  isTimeSeries = false,
  xFormatter: NonNullable<ApexXAxis['labels']>['formatter'] | null = null,
  yFormatter: NonNullable<ApexYAxis['labels']>['formatter'] | null = null,
  tickAmount = undefined,
): ApexOptions => ({
  chart: {
    type: 'area',
    background: 'transparent',
    toolbar: { show: false },
    foreColor: theme.palette.text?.secondary,
  },
  theme: { mode: theme.palette.mode },
  dataLabels: { enabled: false },
  stroke: {
    curve: 'smooth',
    width: 2,
  },
  colors: [theme.palette.primary.main],
  states: { hover: { filter: { type: 'lighten' } } },
  grid: {
    borderColor:
      theme.palette.mode === 'dark'
        ? 'rgba(255, 255, 255, .1)'
        : 'rgba(0, 0, 0, .1)',
    strokeDashArray: 3,
  },
  legend: { show: false },
  tooltip: { theme: theme.palette.mode },
  fill: {
    type: 'gradient',
    gradient: {
      shade: theme.palette.mode,
      shadeIntensity: 1,
      opacityFrom: 0.7,
      opacityTo: 0.1,
      gradientToColors: [
        theme.palette.primary.main!,
        theme.palette.primary.main!,
      ],
    },
  },
  xaxis: {
    type: isTimeSeries ? 'datetime' : 'category',
    tickAmount,
    tickPlacement: 'on',
    labels: {
      formatter: (value: string) => (xFormatter ? xFormatter(value) : value),
      style: {
        fontSize: '12px',
        fontFamily: '"IBM Plex Sans", sans-serif',
      },
    },
    axisBorder: { show: false },
  },
  yaxis: {
    labels: {
      formatter: (value: number) => (yFormatter ? yFormatter(value) : value.toString()),
      style: {
        fontSize: '14px',
        fontFamily: '"IBM Plex Sans", sans-serif',
      },
    },
    axisBorder: { show: false },
  },
});

export interface CustomTooltipOptions {
  _: unknown[];
  seriesIndex: number;
  dataPointIndex: number;
  w: { globals: { initialSeries: Array<{ data: object[] }> } };
}

export type CustomTooltipFunction = (options: CustomTooltipOptions) => unknown | undefined;

function getColors(theme: Theme, isResult: boolean, distributed: boolean) {
  if (isResult) {
    return resultColors(theme.palette.mode === 'dark' ? 400 : 600);
  }
  if (distributed) {
    return colors(theme.palette.mode === 'dark' ? 400 : 600);
  }
  return [theme.palette.primary.main];
}

/**
 * @param {Theme} theme
 * @param {function} xFormatter
 * @param {function} yFormatter
 * @param {boolean} distributed
 * @param {boolean} isTimeSeries
 * @param {boolean} isStacked
 * @param {boolean} legend
 * @param {number | 'dataPoints'} tickAmount
 * @param {boolean} isResult
 * @param {boolean} isFakeData
 * @param {number} max
 * @param {string} emptyChartText
 * @param {function} customTooltip
 * @param {function} onBarClick
 */
interface VerticalBarsChartOptions {
  theme: Theme;
  xFormatter?: NonNullable<ApexXAxis['labels']>['formatter'] | null;
  yFormatter?: NonNullable<ApexYAxis['labels']>['formatter'] | null;
  distributed?: boolean;
  isTimeSeries?: boolean;
  isStacked?: boolean;
  legend?: boolean;
  tickAmount?: ApexXAxis['tickAmount'];
  isResult?: boolean;
  isFakeData?: boolean;
  max?: ApexYAxis['max'];
  emptyChartText?: string;
  customTooltip?: CustomTooltipFunction;
  onBarClick?: onBarClickFunction;
}
export const verticalBarsChartOptions = ({
  theme,
  xFormatter = null,
  yFormatter = null,
  distributed = false,
  isTimeSeries = false,
  isStacked = false,
  legend = false,
  tickAmount,
  isResult = false,
  isFakeData = false,
  max,
  emptyChartText = '',
  customTooltip,
  onBarClick,
}: VerticalBarsChartOptions): ApexOptions => ({
  chart: {
    type: 'bar',
    background: 'transparent',
    toolbar: toolbarOptions,
    foreColor: theme.palette.text?.secondary,
    stacked: isStacked,
    width: '100%',
    height: '100%',
    zoom: { enabled: !isFakeData },
    animations: { enabled: !isFakeData },
    events: {
      dataPointSelection(event, _, config) {
        onBarClick?.(event, config);
      },
    },
  },
  theme: { mode: theme.palette.mode },
  dataLabels: { enabled: false },
  colors: getColors(theme, isResult, distributed),
  states: {
    hover: { filter: { type: isFakeData ? 'none' : 'lighten' } },
    active: { filter: { type: isFakeData ? 'none' : 'lighten' } },
  },
  grid: {
    borderColor:
      theme.palette.mode === 'dark'
        ? 'rgba(255, 255, 255, .1)'
        : 'rgba(0, 0, 0, .1)',
    strokeDashArray: 3,
  },
  legend: {
    show: legend,
    itemMargin: {
      vertical: spacing,
      horizontal: spacingDot5,
    },
    onItemClick: { toggleDataSeries: !isFakeData },
    onItemHover: { highlightDataSeries: !isFakeData },
  },
  tooltip: {
    theme: theme.palette.mode,
    enabled: !isFakeData,
    custom: customTooltip,
  },
  noData: { text: emptyChartText || 'No data to display' },
  xaxis: {
    type: isTimeSeries ? 'datetime' : 'category',
    tickAmount,
    tickPlacement: 'on',
    labels: {
      formatter: (value: string) => (xFormatter ? xFormatter(value) : value),
      style: {
        fontSize: '12px',
        fontFamily: '"IBM Plex Sans", sans-serif',
      },
      show: !isFakeData,
    },
    axisBorder: { show: false },
  },
  fill: { opacity: isFakeData ? 0.1 : 1 },
  yaxis: {
    labels: {
      formatter: (value: number) => (yFormatter ? yFormatter(value) : value.toString()),
      style: { fontFamily: '"IBM Plex Sans", sans-serif' },
    },
    axisBorder: { show: false },
    max,
  },
  plotOptions: {
    bar: {
      horizontal: false,
      barHeight: '30%',
      borderRadius: 4,
      borderRadiusApplication: 'end',
      borderRadiusWhenStacked: 'last',
      distributed,
    },
  },
  ...(isFakeData && {
    subtitle: {
      text: emptyChartText,
      align: 'center',
      offsetY: 130,
    },
  }),
});

export type onBarClickFunction = (event: Event, config: {
  dataPointIndex: number;
  seriesIndex: number;
}) => void;

/**
 * @param {Theme} theme
 * @param {boolean} adjustTicks
 * @param {function} xFormatter
 * @param {function} yFormatter
 * @param {boolean} distributed
 * @param {boolean} stacked
 * @param {boolean} total
 * @param {string[]} categories
 * @param {boolean} legend
 * @param {boolean} isFakeData
 * @param {string} emptyChartText
 * @param {function} onBarClick
 */
interface HorizontalBarsChartOptions {
  theme: Theme;
  adjustTicks?: boolean;
  xFormatter?: ((val: number) => string | string[]) | null;
  yFormatter?: ((val: string) => string) | null;
  distributed?: boolean;
  stacked?: boolean;
  total?: boolean;
  categories?: string[] | string[][] | null;
  legend?: boolean;
  isFakeData?: boolean;
  emptyChartText?: string;
  onBarClick?: onBarClickFunction;
}
export const horizontalBarsChartOptions = ({
  theme,
  adjustTicks = false,
  xFormatter = null,
  yFormatter = null,
  distributed = false,
  stacked = false,
  total = false,
  categories = null,
  legend = false,
  isFakeData = false,
  emptyChartText = '',
  onBarClick,
}: HorizontalBarsChartOptions): ApexOptions => ({
  chart: {
    events: {
      dataPointSelection(event, _, config?) {
        onBarClick?.(event, config);
      },
    },
    type: 'bar',
    background: 'transparent',
    toolbar: toolbarOptions,
    foreColor: theme.palette.text?.secondary,
    stacked,
    width: '100%',
    height: '100%',
    zoom: { enabled: !isFakeData },
    animations: { enabled: !isFakeData },
  },
  theme: { mode: theme.palette.mode },
  dataLabels: { enabled: false },
  colors: [
    theme.palette.primary.main,
    ...colors(theme.palette.mode === 'dark' ? 400 : 600),
  ],
  states: { hover: { filter: { type: isFakeData ? 'none' : 'lighten' } } },
  fill: { opacity: isFakeData ? 0.1 : 0.9 },
  grid: {
    borderColor:
      theme.palette.mode === 'dark'
        ? 'rgba(255, 255, 255, .1)'
        : 'rgba(0, 0, 0, .1)',
    strokeDashArray: 3,
  },
  legend: {
    show: legend,
    itemMargin: {
      vertical: spacing,
      horizontal: spacingDot5,
    },
  },
  tooltip: {
    enabled: !isFakeData,
    theme: theme.palette.mode,
  },
  noData: { text: emptyChartText || 'No data to display' },
  xaxis: {
    categories: categories ?? [],
    labels: {
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-expect-error apexcharts typescript do not handle horizontal bar chart well
      formatter: (value: number) => (xFormatter ? xFormatter(value) : value.toString()),
      style: { fontFamily: '"IBM Plex Sans", sans-serif' },
    },
    axisBorder: { show: false },
    tickAmount: adjustTicks ? 1 : undefined,
  },
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-expect-error apexcharts typescript do not handle horizontal bar chart well
  yaxis: {
    labels: {
      formatter: (value: string) => (yFormatter ? yFormatter(value) : value),
      style: { fontFamily: '"IBM Plex Sans", sans-serif' },
    },
    axisBorder: { show: false },
  },
  plotOptions: {
    bar: {
      horizontal: true,
      barHeight: '30%',
      borderRadius: 4,
      borderRadiusApplication: 'end',
      borderRadiusWhenStacked: 'last',
      distributed,
      dataLabels: {
        total: {
          enabled: total,
          offsetX: 0,
          style: {
            fontSize: '13px',
            fontWeight: 900,
            fontFamily: '"IBM Plex Sans", sans-serif',
          },
        },
      },
    },
  },
  ...(isFakeData && {
    subtitle: {
      text: emptyChartText,
      align: 'center',
      offsetY: 130,
    },
  }),
});

export const radarChartOptions = (theme: Theme, labels: string[], chartColors = []): ApexOptions => ({
  chart: {
    type: 'radar',
    background: 'transparent',
    toolbar: { show: false },
    offsetY: -20,
  },
  theme: { mode: theme.palette.mode },
  labels,
  states: { hover: { filter: { type: 'lighten' } } },
  legend: { show: false },
  tooltip: { theme: theme.palette.mode },
  fill: {
    opacity: 0.2,
    colors: [theme.palette.primary.main],
  },
  stroke: {
    show: true,
    width: 1,
    colors: [theme.palette.primary.main],
    dashArray: 0,
  },
  markers: {
    shape: 'circle',
    strokeColors: [theme.palette.primary.main!],
    colors: [theme.palette.primary.main!],
  },
  xaxis: {
    labels: {
      style: {
        fontFamily: '"IBM Plex Sans", sans-serif',
        colors: chartColors,
      },
    },
    axisBorder: { show: false },
  },
  yaxis: { show: false },
  plotOptions: {
    radar: {
      polygons: {
        strokeColors:
          theme.palette.mode === 'dark'
            ? 'rgba(255, 255, 255, .1)'
            : 'rgba(0, 0, 0, .1)',
        connectorColors:
          theme.palette.mode === 'dark'
            ? 'rgba(255, 255, 255, .1)'
            : 'rgba(0, 0, 0, .1)',
        fill: { colors: [theme.palette.background.paper!] },
      },
    },
  },
});

/**
 * @param {Theme} theme
 * @param {string[]} labels
 * @param {function} formatter
 * @param {string} legendPosition
 * @param {string[]} chartColors
 * @param {boolean} legend
 * @param {boolean} isFakeData
 */
export const polarAreaChartOptions = (
  theme: Theme,
  labels: string[],
  formatter: NonNullable<ApexYAxis['labels']>['formatter'] | null = null,
  legendPosition: ApexLegend['position'] = 'bottom',
  chartColors: string[] = [],
  legend = true,
  isFakeData = false,
): ApexOptions => {
  const temp = theme.palette.mode === 'dark' ? 400 : 600;
  let chartFinalColors = chartColors;
  if (chartFinalColors.length === 0) {
    chartFinalColors = colors(temp);
    if (labels.length === 2 && labels[0] === 'true') {
      chartFinalColors = [C.green[temp], C.red[temp]];
    } else if (labels.length === 2 && labels[0] === 'false') {
      chartFinalColors = [C.red[temp], C.green[temp]];
    }
  }
  return {
    chart: {
      type: 'polarArea',
      background: 'transparent',
      toolbar: toolbarOptions,
      foreColor: theme.palette.text?.secondary,
      width: '100%',
      height: '100%',
      zoom: { enabled: !isFakeData },
      animations: { enabled: !isFakeData },
    },
    theme: { mode: theme.palette.mode },
    colors: chartFinalColors,
    labels,
    states: { hover: { filter: { type: 'lighten' } } },
    legend: {
      show: legend,
      itemMargin: {
        vertical: spacing,
        horizontal: spacingDot5,
      },
      position: legendPosition,
      fontFamily: '"IBM Plex Sans", sans-serif',
    },
    tooltip: {
      enabled: !isFakeData,
      theme: theme.palette.mode,
      custom: simpleLabelTooltip(theme),
    },
    fill: { opacity: isFakeData ? 0.2 : 0.5 },
    stroke: { show: !isFakeData },
    yaxis: {
      labels: {
        formatter: (value: number) => (formatter ? formatter(value) : value.toString()),
        style: { fontFamily: '"IBM Plex Sans", sans-serif' },
      },
      axisBorder: { show: false },
    },
    plotOptions: {
      polarArea: {
        rings: {
          strokeWidth: 1,
          strokeColor:
            theme.palette.mode === 'dark'
              ? 'rgba(255, 255, 255, .1)'
              : 'rgba(0, 0, 0, .1)',
        },
        spokes: {
          strokeWidth: 1,
          connectorColors:
            theme.palette.mode === 'dark'
              ? 'rgba(255, 255, 255, .1)'
              : 'rgba(0, 0, 0, .1)',
        },
      },
    },
  };
};

/**
 * @param {Theme} theme
 * @param {string[]} labels
 * @param {string} legendPosition
 * @param {boolean} reversed
 * @param {string[]} chartColors
 * @param {boolean} displayLegend
 * @param {boolean} displayLabels
 * @param {boolean} displayValue
 * @param {boolean} displayTooltip
 * @param {number} size
 * @param {boolean} disableAnimation
 * @param {boolean} isFakeData
 * @param {string} emptyChartText
 * @param {function} onClick
 */
interface DonutChartOptions {
  theme: Theme;
  labels: string[];
  legendPosition?: ApexLegend['position'];
  reversed?: boolean;
  chartColors?: string[];
  displayLegend?: boolean;
  displayLabels?: boolean;
  displayValue?: boolean;
  displayTooltip?: boolean;
  size?: number;
  disableAnimation?: boolean;
  isFakeData?: boolean;
  emptyChartText?: string;
  onClick?: onBarClickFunction;
}

export const donutChartOptions = ({
  theme,
  labels,
  legendPosition = 'bottom',
  reversed = false,
  chartColors = [],
  displayLegend = true,
  displayLabels = true,
  displayValue = true,
  displayTooltip = true,
  size = 70,
  disableAnimation = false,
  isFakeData = false,
  emptyChartText = '',
  onClick = undefined,
}: DonutChartOptions): ApexOptions => {
  const temp = theme.palette.mode === 'dark' ? 400 : 600;
  let dataLabelsColors = labels.map(() => theme.palette.text?.primary);
  if (chartColors.length > 0) {
    dataLabelsColors = chartColors.map(n => (n === '#ffffff' ? '#000000' : theme.palette.text?.primary));
  }
  let chartFinalColors = chartColors;
  if (chartFinalColors.length === 0) {
    chartFinalColors = colors(temp);
    if (labels.length === 2 && labels[0] === 'true') {
      if (reversed) {
        chartFinalColors = [C.red[temp], C.green[temp]];
      } else {
        chartFinalColors = [C.green[temp], C.red[temp]];
      }
    } else if (labels.length === 2 && labels[0] === 'false') {
      if (reversed) {
        chartFinalColors = [C.green[temp], C.red[temp]];
      } else {
        chartFinalColors = [C.red[temp], C.green[temp]];
      }
    }
  }
  return {
    chart: {
      type: 'donut',
      background: 'transparent',
      toolbar: toolbarOptions,
      foreColor: theme.palette.text?.secondary,
      width: '100%',
      height: '100%',
      zoom: { enabled: !isFakeData },
      animations: { enabled: !isFakeData && !disableAnimation },
      events: {
        dataPointSelection(event, _, config?) {
          onClick?.(event, config);
        },
      },
    },
    theme: { mode: theme.palette.mode },
    colors: chartFinalColors,
    labels,
    fill: { opacity: isFakeData ? 0.1 : 1 },
    states: { hover: { filter: { type: isFakeData ? 'none' : 'lighten' } } },
    stroke: {
      curve: 'smooth',
      width: 3,
      colors: [theme.palette.background.paper],
    },
    tooltip: {
      enabled: !isFakeData && displayTooltip,
      theme: theme.palette.mode,
      custom: simpleLabelTooltip(theme),
    },
    noData: { text: emptyChartText || 'No data to display' },
    legend: {
      show: displayLegend,
      position: legendPosition,
      itemMargin: {
        vertical: spacing,
        horizontal: spacingDot5,
      },
      fontFamily: '"IBM Plex Sans", sans-serif',
      onItemClick: { toggleDataSeries: !isFakeData },
      onItemHover: { highlightDataSeries: !isFakeData },
    },
    dataLabels: {
      enabled: !isFakeData && displayLabels,
      style: {
        fontSize: '10px',
        fontFamily: '"IBM Plex Sans", sans-serif',
        fontWeight: 600,
        colors: dataLabelsColors,
      },
      background: { enabled: false },
      dropShadow: { enabled: false },
    },
    plotOptions: {
      pie: {
        donut: {
          labels: { value: { show: displayValue } },
          background: 'transparent',
          size: `${size}%`,
        },
      },
    },
  };
};
