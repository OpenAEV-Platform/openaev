import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { fetchExerciseCommunications } from '../../../../../actions/Communication';
import { type CommunicationHelper } from '../../../../../actions/communications/communication-helper';
import { type UserHelper } from '../../../../../actions/helper';
import { fetchPlayers } from '../../../../../actions/users/User';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Communication, type Exercise, type User } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { resolveUserName } from '../../../../../utils/String';

interface Props { exerciseId: Exercise['exercise_id'] }

const MailDistributionByPlayer: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { communications, usersMap } = useHelper((helper: CommunicationHelper & UserHelper) => ({
    communications: helper.getExerciseCommunications(exerciseId),
    usersMap: helper.getUsersMap(),
  }));
  useDataLoader(() => {
    dispatch(fetchExerciseCommunications(exerciseId));
    dispatch(fetchPlayers());
  });

  const communicationsUsers = R.uniq(
    R.flatten(
      R.map(
        (n: Communication) => R.map((u: string) => usersMap[u], n.communication_users),
        communications,
      ),
    ),
  );
  const sortedUsersByCommunicationNumber = R.pipe(
    R.map((n: User) => R.assoc(
      'user_communications_number',
      R.filter(
        (c: Communication) => n && R.includes(n.user_id, c.communication_users),
        communications,
      ).length,
      n,
    )),
    R.sortWith([R.descend(R.prop('user_communications_number'))]),
    R.take(10),
  )(communicationsUsers);
  const totalMailsByUserData = [
    {
      name: t('Total mails'),
      data: sortedUsersByCommunicationNumber.map((u: User & { user_communications_number: number }) => ({
        x: resolveUserName(u),
        y: u.user_communications_number,
      })),
    },
  ];

  return (
    <>
      {communicationsUsers.length > 0 ? (
        <Chart
          options={horizontalBarsChartOptions({ theme })}
          series={totalMailsByUserData}
          type="bar"
          width="100%"
          height={50 + communicationsUsers.length * 50}
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

export default MailDistributionByPlayer;
