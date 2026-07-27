import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';

import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Chart from '../../../../../components/Chart';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Communication, type Exercise, type Team } from '../../../../../utils/api-types';
import { lineChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { getTeamsColors } from '../../../teams/utils';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import { sampleMailsOverTimeByTeam } from './mailsSampleData';

interface Props { exerciseId: Exercise['exercise_id'] }

const MailDistributionOverTime: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { nsdt } = useFormatter();
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

  // Teams may exist before any mail is sent: only render the real chart once
  // at least one series has actual points, otherwise preview sample data.
  const hasData = teamsCommunications.some((serie: { data?: unknown[] }) => (serie.data?.length ?? 0) > 0);

  return (
    <>
      {hasData ? (
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
        // No mail traffic yet: preview the widget with greyed sample data
        // (like every widget of the platform) instead of an empty box.
        <SamplePreview active>
          <Chart
            options={lineChartOptions({
              theme,
              isTimeSeries: true,
              xFormatter: nsdt,
            })}
            series={sampleMailsOverTimeByTeam()}
            type="line"
            width="100%"
            height={350}
          />
        </SamplePreview>
      )}
    </>
  );
};

export default MailDistributionOverTime;
