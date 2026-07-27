import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';

import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Chart from '../../../../../components/Chart';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type InjectExpectationOutput, type Team } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { sampleHorizontalBarHeight, sampleHorizontalBarSeries } from '../../../../../utils/SampleCharts';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
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
    R.filter((n: InjectExpectationOutput) => !R.isEmpty(n.inject_expectation_results) && n?.inject_expectation_team),
    R.groupBy(R.prop('inject_expectation_team')),
    R.toPairs,
    R.map((n: [string, InjectExpectationOutput[]]) => ({
      ...teamsMap[n[0]],
      team_total_score: R.sum(
        R.map((o: InjectExpectationOutput) => o.inject_expectation_score, n[1]),
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

  // Dashboard convention: charts without real data render a greyed-out sample
  // (with a "Sample" chip) instead of a bare empty message.
  const isSample = teamsTotalScores.length === 0;
  const sampleLabels = ['Blue team', 'SOC', 'CERT'];

  return (
    <SamplePreview active={isSample}>
      <Chart
        id="exercise_distribution_score_by_team"
        options={horizontalBarsChartOptions({ theme })}
        series={isSample
          ? sampleHorizontalBarSeries(t('Percent of reached score'), sampleLabels, theme, [85, 62, 38])
          : percentScoreByTeamData}
        type="bar"
        width="100%"
        height={isSample ? sampleHorizontalBarHeight(sampleLabels) : 50 + sortedTeamsByPercentScore.length * 50}
      />
    </SamplePreview>
  );
};

export default ExerciseDistributionScoreByTeamInPercentage;
