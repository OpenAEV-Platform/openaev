import { TimelineOutlined } from '@mui/icons-material';
import { Paper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import Chart from '../../../../components/Chart';
import { useFormatter } from '../../../../components/i18n';
import { type Inject } from '../../../../utils/api-types';
import { areaChartOptions } from '../../../../utils/Charts';
import LessonsPlaceholder from '../LessonsPlaceholder';

interface Props { injects: Inject[] }

const CrysisIntensity: FunctionComponent<Props> = ({ injects }) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const injectsByDate = injects.reduce<Record<string, number>>((acc, inject) => {
    if (inject.inject_sent_at == null) {
      return acc;
    }
    const date = new Date(inject.inject_sent_at);
    date.setHours(0, 0, 0, 0);
    const key = date.toISOString();
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});
  const injectsData = Object.entries(injectsByDate).map(([date, count]) => ({
    x: date,
    y: count,
  }));
  const chartData = [
    {
      name: t('Number of injects'),
      data: injectsData,
    },
  ];
  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 1,
        flex: 1,
        overflow: 'hidden',
      }}
    >
      {injectsData.length > 0 ? (
        <Chart
          options={areaChartOptions(theme, true, nsdt, null, undefined)}
          series={chartData}
          type="area"
          width="100%"
          height={350}
        />
      ) : (
        <LessonsPlaceholder
          icon={TimelineOutlined}
          message={t('No data to display or the simulation has not started yet')}
        />
      )}
    </Paper>
  );
};

export default CrysisIntensity;
