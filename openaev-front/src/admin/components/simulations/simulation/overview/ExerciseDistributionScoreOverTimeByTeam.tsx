import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Chart from '../../../../../components/Chart';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type InjectExpectationOutput } from '../../../../../utils/api-types';
import { lineChartOptions } from '../../../../../utils/Charts';
import { sampleScoreOverTimeSeries } from '../../../../../utils/SampleCharts';
import { ascendBy } from '../../../../../utils/Sorting';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import { computeTeamsColors } from './DistributionUtils';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreOverTimeByTeam: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { nsdt } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectExpectations, teams, teamsMap } = useHelper((helper: InjectHelper & TeamsHelper) => ({
    injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
    teams: helper.getExerciseTeams(exerciseId),
    teamsMap: helper.getTeamsMap(),
  }));

  const teamsColors = computeTeamsColors(teams, theme);

  const scoredTeamExpectations = injectExpectations.filter(
    (n: InjectExpectationOutput) => n.inject_expectation_score != null && n.inject_expectation_team && n.inject_expectation_user === null,
  );
  const expectationsByTeam = Object.groupBy(scoredTeamExpectations, (item: InjectExpectationOutput) => String(item.inject_expectation_team));
  const teamsScores = Object.entries(expectationsByTeam).map(([teamId, expectations = []]) => {
    let cumulation = 0;
    return {
      name: teamsMap[teamId]?.team_name,
      color: teamsColors[teamId],
      data: expectations.toSorted(ascendBy('inject_expectation_updated_at')).map((i: InjectExpectationOutput) => {
        cumulation += i.inject_expectation_score ?? 0;
        return {
          x: i.inject_expectation_updated_at,
          y: cumulation,
        };
      }),
    };
  });

  // Dashboard convention: charts without real data render a greyed-out sample
  // (with a "Sample" chip) instead of a bare empty message.
  const isSample = teamsScores.length === 0;

  return (
    <SamplePreview active={isSample}>
      <Chart
        id="exercise_distribution_score_over_time_by_team"
        options={lineChartOptions({
          theme,
          isTimeSeries: true,
          xFormatter: nsdt,
        })}
        series={isSample
          ? sampleScoreOverTimeSeries(['Blue team', 'SOC'], theme)
          : teamsScores}
        type="line"
        width="100%"
        height={350}
      />
    </SamplePreview>
  );
};
export default ExerciseDistributionScoreOverTimeByTeam;
