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

  const teamsByPercentScore = R.pipe(
    R.filter((n: InjectExpectationOutput) => !!n.inject_expectation_team && n.inject_expectation_user === null),
    R.groupBy((n: InjectExpectationOutput) => n.inject_expectation_team ?? ''),
    R.toPairs,
    // A team shows up once at least one of its expectations has been evaluated
    R.filter(([, expectations]: [string, InjectExpectationOutput[]]) => expectations.some(e => e.inject_expectation_score != null)),
    R.map(([teamId, expectations]: [string, InjectExpectationOutput[]]) => {
      const reachedScore = R.sum(expectations.map(e => e.inject_expectation_score ?? 0));
      const expectedScore = R.sum(expectations.map(e => e.inject_expectation_expected_score));
      return {
        ...teamsMap[teamId],
        team_total_percent_score: Math.round((reachedScore * 100) / (expectedScore || 1)),
      };
    }),
  )(injectExpectations);

  const teamsColors = computeTeamsColors(teams, theme);
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
  const isSample = teamsByPercentScore.length === 0;
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
