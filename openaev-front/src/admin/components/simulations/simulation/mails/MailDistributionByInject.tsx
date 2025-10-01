import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { fetchExerciseInjects } from '../../../../../actions/Inject';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type Inject } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';

interface Props { exerciseId: Exercise['exercise_id'] }

const MailDistributionByInject: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { injects } = useHelper((helper: InjectHelper) => ({ injects: helper.getScenarioInjects(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseInjects(exerciseId));
  });

  const sortedInjectsByCommunicationNumber = R.pipe(
    R.sortWith([R.descend(R.prop('inject_communications_number'))]),
    R.take(10),
  )(injects || []);
  const totalMailsByInjectData = [
    {
      name: t('Total mails'),
      data: sortedInjectsByCommunicationNumber.map((i: Inject) => ({
        x: i.inject_title,
        y: i.inject_communications_number,
      })),
    },
  ];

  return (
    <>
      {sortedInjectsByCommunicationNumber.length > 0 ? (
        <Chart
          options={horizontalBarsChartOptions({ theme })}
          series={totalMailsByInjectData}
          type="bar"
          width="100%"
          height={50 + sortedInjectsByCommunicationNumber.length * 50}
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

export default MailDistributionByInject;
