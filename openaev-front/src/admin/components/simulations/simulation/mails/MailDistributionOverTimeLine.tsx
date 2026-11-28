import { useTheme } from '@mui/material/styles';
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
import { ascendBy } from '../../../../../utils/Sorting';
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

  const teamsColors = getTeamsColors(teams);
  // the n-th mail of a team, by reception date, brings its total to n
  const teamsCommunications = (teams as Team[]).map((team: Team) => ({
    name: team.team_name,
    color: teamsColors[team.team_id],
    data: (team.team_communications || []).toSorted(ascendBy('communication_received_at')).map((c: Communication, index: number) => ({
      x: c.communication_received_at,
      y: index + 1,
    })),
  }));

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
