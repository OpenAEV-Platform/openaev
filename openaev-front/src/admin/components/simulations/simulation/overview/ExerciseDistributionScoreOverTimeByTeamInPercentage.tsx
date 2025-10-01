import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type InjectExpectation } from '../../../../../utils/api-types';
import { lineChartOptions } from '../../../../../utils/Charts';
import { computeTeamsColors } from './DistributionUtils';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreOverTimeByTeamInPercentage: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectExpectations, teams, teamsMap } = useHelper((helper: InjectHelper & TeamsHelper) => ({
    injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
    teams: helper.getExerciseTeams(exerciseId),
    teamsMap: helper.getTeamsMap(),
  }));
  const teamsTotalScores = R.pipe(
    R.filter((n: InjectExpectation) => !R.isEmpty(n.inject_expectation_results) && n?.inject_expectation_team),
    R.groupBy(R.prop('inject_expectation_team')),
    R.toPairs,
    R.map((n: [string, InjectExpectation[]]) => ({
      ...teamsMap[n[0]],
      team_total_score: R.sum(
        R.map((o: InjectExpectation) => o.inject_expectation_score, n[1]),
      ),
    })),
  )(injectExpectations);
  const teamsColors = computeTeamsColors(teams, theme);
  let cumulation = 0;
  const teamsPercentScoresData = R.pipe(
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
            return R.assoc(
              'inject_expectation_percent_score',
              Math.round(
                (cumulation * 100)
                / (teamsMap[n[0]] && teamsMap[n[0]].team_injects_expectations_total_expected_score_by_exercise
                  ? teamsMap[n[0]]
                    .team_injects_expectations_total_expected_score_by_exercise[exerciseId]
                  : 1),
              ),
              i,
            );
          }),
        )(n[1]),
      ];
    }),
    R.map((n: [string, Array<InjectExpectation & { inject_expectation_percent_score: number }>]) => ({
      name: teamsMap[n[0]]?.team_name,
      color: teamsColors[n[0]],
      data: n[1].map(i => ({
        x: i.inject_expectation_updated_at,
        y: i.inject_expectation_percent_score,
      })),
    })),
  )(injectExpectations);

  return (
    <>
      {teamsTotalScores.length > 0 ? (
        <Chart
          id="exercise_distribution_score_over_time"
          options={lineChartOptions({
            theme,
            isTimeSeries: true,
            xFormatter: nsdt,
          })}
          series={teamsPercentScoresData}
          type="line"
          width="100%"
          height={350}
        />
      ) : (
        <Empty
          id="exercise_distribution_score_over_time"
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};

export default ExerciseDistributionScoreOverTimeByTeamInPercentage;
