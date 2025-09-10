import { type UserHelper } from '../../../actions/helper';
import { updateTeamPlayers } from '../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../actions/teams/team-helper';
import { type Page } from '../../../components/common/queryable/Page';
import { useHelper } from '../../../store';
import { type SearchPaginationInput, type Team, type TeamOutput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import { INHERITED_CONTEXT } from '../../../utils/permissions/types';
import { PermissionsContext, type PermissionsContextType, TeamContext, type TeamContextType } from '../common/Context';
import TeamsComponent from '../components/teams/Teams';
import { type UserStore } from './players/Player';

const Teams = () => {
  const dispatch = useAppDispatch();
  const { teams }: { teams: Team[] } = useHelper((helper: UserHelper & TeamsHelper) => ({
    user: helper.getMe(),
    teams: helper.getTeams(),
  }));

  const permissionsContext: PermissionsContextType = {
    permissions: {
      readOnly: false,
      canManage: false,
      canAccess: false,
      canDelete: false,
      canLaunch: false,
      isRunning: false,
    },
    inherited_context: INHERITED_CONTEXT.NONE,
  };

  const teamContext: TeamContextType = {
    onAddUsersTeam(teamId: Team['team_id'], userIds: UserStore['user_id'][]): Promise<void> {
      return dispatch(updateTeamPlayers(teamId, { team_users: [...(teams.find(t => t.team_id === teamId)?.team_users || []), ...userIds] }));
    },
    onRemoveUsersTeam(teamId: Team['team_id'], userIds: UserStore['user_id'][]): Promise<void> {
      return dispatch(updateTeamPlayers(teamId, { team_users: [...(teams.find(t => t.team_id === teamId)?.team_users?.filter(u => !userIds.includes(u)) || [])] }));
    },
    searchTeams(_: SearchPaginationInput): Promise<{ data: Page<TeamOutput> }> {
      return new Promise<{ data: Page<TeamOutput> }>(() => {
      });
    },
  };

  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <TeamContext.Provider value={teamContext}>
        <TeamsComponent />
      </TeamContext.Provider>
    </PermissionsContext.Provider>
  );
};

export default Teams;
