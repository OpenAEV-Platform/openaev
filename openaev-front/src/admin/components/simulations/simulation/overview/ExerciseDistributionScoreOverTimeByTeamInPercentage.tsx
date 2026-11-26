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

const ExerciseDistributionScoreOverTimeByTeamInPercentage: FunctionComponent<Props> = ({ exerciseId }) => {
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
  let cumulation = 0;
  const scoredTeamExpectations = injectExpectations.filter(
    (n: InjectExpectationOutput) => n?.inject_expectation_score != null && n?.inject_expectation_team && n?.inject_expectation_user === null,
  );
  const expectationsByTeam = Object.groupBy(scoredTeamExpectations, (item: InjectExpectationOutput) => String(item.inject_expectation_team));
  const percentScoresByTeam = Object.entries(expectationsByTeam).map((n: [string, InjectExpectationOutput[] | undefined]) => {
    cumulation = 0;
    return [
      n[0],
      n[1]!.toSorted(ascendBy('inject_expectation_updated_at')).map((i: InjectExpectationOutput) => {
        cumulation += i.inject_expectation_score ?? 0;
        return {
          ...i,
          inject_expectation_percent_score: Math.round(
            (cumulation * 100)
            / (teamsMap[n[0]] && teamsMap[n[0]].team_injects_expectations_total_expected_score_by_exercise
              ? teamsMap[n[0]]
                .team_injects_expectations_total_expected_score_by_exercise[exerciseId]
              : 1),
          ),
        };
      }),
    ] as const;
  });
  const teamsPercentScoresData = percentScoresByTeam.map(n => ({
    name: teamsMap[n[0]]?.team_name,
    color: teamsColors[n[0]],
    data: n[1].map(i => ({
      x: i.inject_expectation_updated_at,
      y: i.inject_expectation_percent_score,
    })),
  }));

  // Dashboard convention: charts without real data render a greyed-out sample
  // (with a "Sample" chip) instead of a bare empty message.
  const isSample = teamsPercentScoresData.length === 0;

  return (
    <SamplePreview active={isSample}>
      <Chart
        id="exercise_distribution_score_over_time"
        options={lineChartOptions({
          theme,
          isTimeSeries: true,
          xFormatter: nsdt,
        })}
        series={isSample
          ? sampleScoreOverTimeSeries(['Blue team', 'SOC'], theme)
          : teamsPercentScoresData}
        type="line"
        width="100%"
        height={350}
      />
    </SamplePreview>
  );
};

export default ExerciseDistributionScoreOverTimeByTeamInPercentage;
