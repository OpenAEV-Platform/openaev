import { type ApexOptions } from 'apexcharts';
import { type FunctionComponent } from 'react';
import ReactApexChart from 'react-apexcharts';

/**
 * Print-safe ApexCharts wrapper.
 *
 * - Imported STATICALLY (unlike components/Chart which is lazy): the render
 *   route is itself a lazy chunk, and a nested Suspense would make the
 *   readiness marker fire before charts mount under headless capture.
 * - Animations are force-disabled and sizes are fixed pixels so the printed
 *   output is deterministic (no responsive resize dependency).
 */

interface Props {
  options: ApexOptions;
  series: ApexOptions['series'];
  type: NonNullable<ApexChart['type']>;
  width: number;
  height: number;
}

const PrintChart: FunctionComponent<Props> = ({ options, series, type, width, height }) => {
  const printOptions: ApexOptions = {
    ...options,
    chart: {
      ...options.chart,
      animations: { enabled: false },
      toolbar: { show: false },
      background: 'transparent',
    },
    tooltip: {
      ...options.tooltip,
      enabled: false,
    },
  };
  return (
    <ReactApexChart
      options={printOptions}
      series={series}
      type={type}
      width={width}
      height={height}
    />
  );
};

export default PrintChart;
