import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { fetchExerciseCommunications } from '../../../../../actions/Communication';
import { type CommunicationHelper } from '../../../../../actions/communications/communication-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Communication, type Exercise } from '../../../../../utils/api-types';
import { areaChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';

interface Props { exerciseId: Exercise['exercise_id'] }

const MailDistributionOverTimeChart: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { communications } = useHelper((helper: CommunicationHelper) => ({ communications: helper.getExerciseCommunications(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseCommunications(exerciseId));
  });

  let cumulation = 0;
  const communicationsOverTime = R.pipe(
    R.sortWith([R.ascend(R.prop('communication_received_at'))]),
    R.map((i: Communication) => {
      cumulation += 1;
      return R.assoc('communication_cumulated_number', cumulation, i);
    }),
  )(communications);
  const communicationsData = [
    {
      name: t('Total mails'),
      data: communicationsOverTime.map((c: Communication & { communication_cumulated_number: number }) => ({
        x: c.communication_received_at,
        y: c.communication_cumulated_number,
      })),
    },
  ];

  return (
    <>
      {communicationsOverTime.length > 0 ? (
        <Chart
          options={areaChartOptions(theme, true, nsdt, null, undefined)}
          series={communicationsData}
          type="area"
          width="100%"
          height={350}
        />
      ) : (
        <Empty
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};

export default MailDistributionOverTimeChart;
