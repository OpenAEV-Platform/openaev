import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { fetchExerciseTeams } from '../../../../actions/Exercise';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Exercise, type Team } from '../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../utils/Charts';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { getTeamsColors } from '../../teams/utils';

interface Props { exerciseId: Exercise['exercise_id'] }

const InjectDistributionByTeam: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { teams } = useHelper((helper: TeamsHelper) => ({ teams: helper.getExerciseTeams(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
  });

  const teamsColors = getTeamsColors(teams);
  const sortedTeamsByExpectedScore = R.pipe(
    R.sortWith([
      R.descend(R.prop('team_injects_expectations_total_expected_score')),
    ]),
    R.take(10),
  )(teams || []);
  const expectedScoreByTeamData = [
    {
      name: t('Total expected score'),
      data: sortedTeamsByExpectedScore.map((a: Team) => ({
        x: a.team_name,
        y: a.team_injects_expectations_total_expected_score,
        fillColor: teamsColors[a.team_id],
      })),
    },
  ];

  return (
    <>
      {sortedTeamsByExpectedScore.length > 0 ? (
        <Chart
          options={horizontalBarsChartOptions({ theme })}
          series={expectedScoreByTeamData}
          type="bar"
          width="100%"
          height={50 + sortedTeamsByExpectedScore.length * 50}
        />
      ) : (
        <Empty message={t('No teams in this exercise.')} />
      )}
    </>
  );
};

export default InjectDistributionByTeam;
