import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { fetchExerciseCommunications } from '../../../../../actions/Communication';
import { type CommunicationHelper } from '../../../../../actions/communications/communication-helper';
import { type UserHelper } from '../../../../../actions/helper';
import { fetchPlayers } from '../../../../../actions/users/User';
import Chart from '../../../../../components/Chart';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Communication, type Exercise, type User } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { descendBy } from '../../../../../utils/Sorting';
import { resolveUserName } from '../../../../../utils/String';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import { sampleMailsByPlayer } from './mailsSampleData';

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

  // usersMap holds one object per user id, so the Set keeps each user once
  const communicationsUsers = [...new Set(
    communications.flatMap(
      (n: Communication) => n.communication_users!.map((u: string) => usersMap[u]),
    ),
  )];
  const usersWithCommunicationNumber = (communicationsUsers as User[]).map((n: User) => ({
    ...n,
    user_communications_number: communications.filter((c: Communication) => n && c.communication_users!.includes(n.user_id!)).length,
  }));
  const sortedUsersByCommunicationNumber = usersWithCommunicationNumber.toSorted(descendBy('user_communications_number')).slice(0, 10);
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
        // No mail traffic yet: preview the widget with greyed sample data
        // (like every widget of the platform) instead of an empty box.
        <SamplePreview active>
          <Chart
            options={horizontalBarsChartOptions({ theme })}
            series={sampleMailsByPlayer(t('Total mails'))}
            type="bar"
            width="100%"
            height={300}
          />
        </SamplePreview>
      )}
    </>
  );
};

export default MailDistributionByPlayer;
