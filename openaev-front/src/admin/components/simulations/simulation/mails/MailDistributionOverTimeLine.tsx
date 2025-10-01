import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Communication, type Exercise, type Team } from '../../../../../utils/api-types';
import { lineChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { getTeamsColors } from '../../../teams/utils';

interface Props { exerciseId: Exercise['exercise_id'] }

const MailDistributionOverTime: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { teams } = useHelper((helper: TeamsHelper) => ({ teams: helper.getExerciseTeams(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
  });

  let cumulation = 0;
  const teamsColors = getTeamsColors(teams);
  const teamsCommunications = R.pipe(
    R.map((n: Team) => {
      cumulation = 0;
      return R.assoc(
        'team_communications',
        R.pipe(
          R.sortWith([R.ascend(R.prop('communication_received_at'))]),
          R.map((i: Communication) => {
            cumulation += 1;
            return R.assoc('communication_cumulated_number', cumulation, i);
          }),
        )(n.team_communications || []),
        n,
      );
    }),
    R.map((a: Team & { team_communications: Array<Communication & { communication_cumulated_number: number }> }) => ({
      name: a.team_name,
      color: teamsColors[a.team_id],
      data: a.team_communications?.map((c: Communication & { communication_cumulated_number: number }) => ({
        x: c.communication_received_at,
        y: c.communication_cumulated_number,
      })),
    })),
  )(teams);

  return (
    <>
      {teamsCommunications.length > 0 ? (
        <Chart
          options={lineChartOptions({
            theme,
            isTimeSeries: true,
            xFormatter: nsdt,
          })}
          series={teamsCommunications}
          type="line"
          width="100%"
          height={350}
        />
      ) : (
        <Empty
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};

export default MailDistributionOverTime;
