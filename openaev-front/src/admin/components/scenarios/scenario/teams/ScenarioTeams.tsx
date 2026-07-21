import { type FunctionComponent, useContext } from 'react';
import { useParams } from 'react-router';

import { fetchScenarioTeams } from '../../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { type Scenario, type Team } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, TeamContext } from '../../../common/Context';
import ContextualTeams from '../../../components/teams/ContextualTeams';
import UpdateTeams from '../../../components/teams/UpdateTeams';
import ConfigurationFab from '../ConfigurationFab';
import teamContextForScenario from './teamContextForScenario';

interface Props { scenarioTeamsUsers: Scenario['scenario_teams_users'] }

const ScenarioTeams: FunctionComponent<Props> = ({ scenarioTeamsUsers }) => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);

  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { teamsStore }: { teamsStore: Team[] } = useHelper((helper: ScenariosHelper) => ({ teamsStore: helper.getScenarioTeams(scenarioId) }));
  useDataLoader(() => {
    dispatch(fetchScenarioTeams(scenarioId));
  });

  return (
    <TeamContext.Provider value={teamContextForScenario(scenarioId, scenarioTeamsUsers)}>
      {/* No inner Paper / section title: the Configuration tab already labels
          this section, and the list sits directly on the drawer surface. */}
      {permissions.canManage && (
        <ConfigurationFab>
          <UpdateTeams addedTeamIds={teamsStore.map((team: Team) => team.team_id)} />
        </ConfigurationFab>
      )}
      <div data-testid="teams-list-section">
        <ContextualTeams teams={teamsStore} />
      </div>
    </TeamContext.Provider>
  );
};

export default ScenarioTeams;
