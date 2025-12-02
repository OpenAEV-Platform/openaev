import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';
import { useParams } from 'react-router';

import { fetchScenarioTeams } from '../../../../../actions/scenarios/scenario-actions';
import { getScenarioTeamsSelector } from '../../../../../actions/selectors';
import { useFormatter } from '../../../../../components/i18n';
import { useSelectorHelper } from '../../../../../store';
import { type Scenario, type Team } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import ContextualTeams from '../../../components/teams/ContextualTeams';
import UpdateTeams from '../../../components/teams/UpdateTeams';
import teamContextForScenario from './teamContextForScenario';

interface Props { scenarioTeamsUsers: Scenario['scenario_teams_users'] }

const ScenarioTeams: FunctionComponent<Props> = ({ scenarioTeamsUsers }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);
  const theme = useTheme();

  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const teamsStore = useSelectorHelper(state => getScenarioTeamsSelector(scenarioId, state));
  useDataLoader(() => {
    dispatch(fetchScenarioTeams(scenarioId));
  });

  return (
    <TeamContext.Provider value={teamContextForScenario(scenarioId, scenarioTeamsUsers)}>
      <div style={{
        display: 'grid',
        gap: `0 ${theme.spacing(3)}`,
        gridTemplateRows: 'min-content 1fr',
      }}
      >
        <Typography variant="h4">
          {t('Teams')}
          {permissions.canManage
            && (
              <UpdateTeams
                addedTeamIds={(teamsStore as Team[]).map((team: Team) => team.team_id)}
              />
            )}
        </Typography>
        <Paper sx={{ padding: theme.spacing(2) }} variant="outlined">
          <ContextualTeams teams={teamsStore as Team[]} />
        </Paper>
      </div>
    </TeamContext.Provider>
  );
};

export default ScenarioTeams;
