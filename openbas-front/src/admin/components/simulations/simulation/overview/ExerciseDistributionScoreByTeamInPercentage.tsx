import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type InjectExpectation, type Team } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { computeTeamsColors } from './DistributionUtils';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreByTeamInPercentage: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
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
  const teamsByPercentScore = R.map(
    (n: Team) => R.assoc(
      'team_total_percent_score',
      Math.round(
        (n.team_injects_expectations_total_score_by_exercise ? n.team_injects_expectations_total_score_by_exercise[exerciseId] * 100 : 0)
        / (n.team_injects_expectations_total_expected_score_by_exercise ? n.team_injects_expectations_total_expected_score_by_exercise[exerciseId] : 1),
      ),
      n,
    ),
    teamsTotalScores,
  );
  const sortedTeamsByPercentScore = R.pipe(
    R.sortWith([R.descend(R.prop('team_total_percent_score'))]),
    R.take(10),
  )(teamsByPercentScore || []);
  const percentScoreByTeamData = [
    {
      name: t('Percent of reached score'),
      data: sortedTeamsByPercentScore.map((a: Team & { team_total_percent_score: number }) => ({
        x: a.team_name,
        y: a.team_total_percent_score || null,
        fillColor: teamsColors[a.team_id] ?? '',
      })),
    },
  ];

  return (
    <>
      {teamsTotalScores.length > 0 ? (
        <Chart
          id="exercise_distribution_score_by_team"
          options={horizontalBarsChartOptions({ theme })}
          series={percentScoreByTeamData}
          type="bar"
          width="100%"
          height={50 + sortedTeamsByPercentScore.length * 50}
        />
      ) : (
        <Empty
          id="exercise_distribution_score_by_team"
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};

export default ExerciseDistributionScoreByTeamInPercentage;
