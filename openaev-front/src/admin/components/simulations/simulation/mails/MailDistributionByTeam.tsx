import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';

import { fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import Chart from '../../../../../components/Chart';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type Team } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import { computeTeamsColors } from '../overview/DistributionUtils';
import { sampleMailsByTeam } from './mailsSampleData';

interface Props { exerciseId: Exercise['exercise_id'] }

const MailDistributionByTeam: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { teams } = useHelper((helper: TeamsHelper) => ({ teams: helper.getTeams() }));
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
  });

  const teamsColors = computeTeamsColors(teams, theme);
  const sortedTeamsByCommunicationNumber = R.pipe(
    R.map((a: Team) => R.assoc(
      'team_communications_number',
      a.team_communications?.length,
      a,
    )),
    R.sortWith([R.descend(R.prop('team_communications_number'))]),
    R.take(10),
  )(teams || []);
  const totalMailsByTeamData = [
    {
      name: t('Total mails'),
      data: sortedTeamsByCommunicationNumber.map((a: Team & { team_communications_number: number }) => ({
        x: a.team_name,
        y: a.team_communications_number,
        fillColor: teamsColors[a.team_id],
      })),
    },
  ];

  // Teams may exist before any mail is sent: only render the real chart once
  // at least one team has mail traffic, otherwise preview sample data.
  const hasData = sortedTeamsByCommunicationNumber.some(
    (team: { team_communications_number?: number }) => (team.team_communications_number ?? 0) > 0,
  );

  return (
    <>
      {hasData ? (
        <Chart
          options={horizontalBarsChartOptions({ theme })}
          series={totalMailsByTeamData}
          type="bar"
          width="100%"
          height={50 + sortedTeamsByCommunicationNumber.length * 50}
        />
      ) : (
        // No mail traffic yet: preview the widget with greyed sample data
        // (like every widget of the platform) instead of an empty box.
        <SamplePreview active>
          <Chart
            options={horizontalBarsChartOptions({ theme })}
            series={sampleMailsByTeam(t('Total mails'))}
            type="bar"
            width="100%"
            height={200}
          />
        </SamplePreview>
      )}
    </>
  );
};

export default MailDistributionByTeam;
