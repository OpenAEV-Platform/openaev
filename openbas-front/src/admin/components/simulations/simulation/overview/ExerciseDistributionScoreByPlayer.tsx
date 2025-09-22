import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type UserHelper } from '../../../../../actions/helper';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type InjectExpectation, type User } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { resolveUserName } from '../../../../../utils/String';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreByPlayer: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectExpectations, usersMap } = useHelper((helper: InjectHelper & UserHelper) => ({
    injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
    usersMap: helper.getUsersMap(),
  }));

  const usersTotalScores = R.pipe(
    R.filter(
      (n: InjectExpectation) => !R.isEmpty(n.inject_expectation_results)
        && n.inject_expectation_user !== null,
    ),
    R.groupBy(R.prop('inject_expectation_user')),
    R.toPairs,
    R.map((n: [string, InjectExpectation[]]) => ({
      ...usersMap[n[0]],
      user_total_score: R.sum(R.map((o: InjectExpectation) => o.inject_expectation_score, n[1])),
    })),
  )(injectExpectations);

  const sortedUsersByTotalScore = R.pipe(
    R.sortWith([R.descend(R.prop('user_total_score'))]),
    R.take(10),
  )(usersTotalScores);

  const totalScoreByUserData = [
    {
      name: t('Total score'),
      data: sortedUsersByTotalScore.map((u: User & { user_total_score: number }) => ({
        x: resolveUserName(u) ?? '',
        y: u.user_total_score || null,
      })),
    },
  ];

  return (
    <>
      {usersTotalScores.length > 0 ? (
        <Chart
          id="exercise_distribution_total_score_by_player"
          options={horizontalBarsChartOptions({ theme })}
          series={totalScoreByUserData}
          type="bar"
          width="100%"
          height={50 + usersTotalScores.length * 50}
        />
      ) : (
        <Empty
          id="exercise_distribution_total_score_by_player"
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};
export default ExerciseDistributionScoreByPlayer;
