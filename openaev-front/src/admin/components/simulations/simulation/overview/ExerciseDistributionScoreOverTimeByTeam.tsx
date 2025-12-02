import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { getExerciseInjectExpectationsSelector, getExerciseTeamsSelector, getTeamsMapSelector } from '../../../../../actions/selectors';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useSelectorHelper } from '../../../../../store';
import { type Exercise, type InjectExpectation, type Team } from '../../../../../utils/api-types';
import { lineChartOptions } from '../../../../../utils/Charts';
import { computeTeamsColors } from './DistributionUtils';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreOverTimeByTeam: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const injectExpectations = useSelectorHelper(state => getExerciseInjectExpectationsSelector(exerciseId, state));
  const teams = useSelectorHelper(state => getExerciseTeamsSelector(exerciseId, state));
  const teamsMap = useSelectorHelper(getTeamsMapSelector);

  const teamsColors = computeTeamsColors(teams as Team[], theme);

  let cumulation = 0;
  const teamsScores = R.pipe(
    R.filter((n: InjectExpectation) => !R.isEmpty(n.inject_expectation_results) && n?.inject_expectation_team && n?.inject_expectation_user === null),
    R.groupBy(R.prop('inject_expectation_team')),
    R.toPairs,
    R.map((n: [string, InjectExpectation[]]) => {
      cumulation = 0;
      return [
        n[0],
        R.pipe(
          R.sortWith([R.ascend(R.prop('inject_expectation_updated_at'))]),
          R.map((i: InjectExpectation) => {
            cumulation += i.inject_expectation_score ?? 0;
            return R.assoc('inject_expectation_cumulated_score', cumulation, i);
          }),
        )(n[1]),
      ];
    }),
    R.map((n: [string, Array<InjectExpectation & { inject_expectation_cumulated_score: number }>]) => ({
      name: teamsMap[n[0]]?.team_name,
      color: teamsColors[n[0]],
      data: n[1].map((i: InjectExpectation & { inject_expectation_cumulated_score: number }) => ({
        x: i.inject_expectation_updated_at,
        y: i.inject_expectation_cumulated_score,
      })),
    })),
  )(injectExpectations);

  return (
    <>
      {teamsScores.length > 0 ? (
        <Chart
          id="exercise_distribution_score_over_time_by_team"
          options={lineChartOptions({
            theme,
            isTimeSeries: true,
            xFormatter: nsdt,
          })}
          series={teamsScores}
          type="line"
          width="100%"
          height={350}
        />
      ) : (
        <Empty
          id="exercise_distribution_score_over_time_by_team"
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};
export default ExerciseDistributionScoreOverTimeByTeam;
