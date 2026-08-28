import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { lineChartOptions } from '../../../../../utils/Charts';
import { type ModuleDataState, type TrendPoint } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError } from './ModuleSection';
import PrintChart from './PrintChart';

/**
 * Score trend over the report window: per-bucket success rate (percent of
 * validated expectations that succeeded), computed client-side from the
 * temporal SUCCESS / FAILED series.
 */

interface Props { trends: ModuleDataState<TrendPoint[]> }

const ScoreTrendsModule: FunctionComponent<Props> = ({ trends }) => {
  const theme = useTheme();
  const { t, fsd } = useFormatter();

  if (trends.status === 'error') return <ModuleError />;
  const points = (trends.data ?? []).filter(point => point.success + point.failed > 0);
  if (trends.status !== 'success' || points.length === 0) {
    return <ModuleEmpty message={t('No validated expectation over the selected time range.')} />;
  }

  const series = [{
    name: t('Score'),
    data: points.map(point => ({
      x: point.date,
      y: Math.round((point.success / (point.success + point.failed)) * 100),
    })),
  }];

  const options = {
    ...lineChartOptions({
      theme,
      isTimeSeries: true,
      xFormatter: value => String(fsd(value)),
      yFormatter: value => `${value}%`,
    }),
    yaxis: {
      min: 0,
      max: 100,
      labels: { formatter: (value: number) => `${Math.round(value)}%` },
    },
    colors: [theme.palette.primary.main],
    stroke: {
      curve: 'smooth' as const,
      width: 2,
    },
    markers: {
      size: 3,
      strokeWidth: 0,
    },
  };

  return (
    <Box>
      <PrintChart
        options={options}
        series={series}
        type="line"
        width={680}
        height={260}
      />
    </Box>
  );
};

export default ScoreTrendsModule;
