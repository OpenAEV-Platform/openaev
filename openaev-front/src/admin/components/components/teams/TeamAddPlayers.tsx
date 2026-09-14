import { PersonOutlined } from '@mui/icons-material';
import { type FunctionComponent, useContext, useMemo, useState } from 'react';

import { type OrganizationHelper } from '../../../../actions/helper';
import { searchPlayers } from '../../../../actions/players/player-actions';
import { fetchTeamPlayers } from '../../../../actions/teams/team-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { useHelper } from '../../../../store';
import { type Organization, type PlayerOutput, type Team, type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { resolveUserName } from '../../../../utils/String';
import { TeamContext } from '../../common/Context';
import CreatePlayer from '../../teams/players/CreatePlayer';
import { type UserStore } from '../../teams/players/Player';

interface Props {
  addedUsersIds: UserStore['user_id'][];
  teamId: Team['team_id'];
}

const TeamAddPlayers: FunctionComponent<Props> = ({ addedUsersIds, teamId }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);
  const [usersIds, setUsersIds] = useState<UserStore['user_id'][]>([]);

  const { onAddUsersTeam } = useContext(TeamContext);

  const { organizationsMap }: { organizationsMap: Record<string, Organization> } = useHelper(
    (helper: OrganizationHelper) => ({ organizationsMap: helper.getOrganizationsMap() }),
  );

  const toggleUser = (userId: string) => {
    if (usersIds.includes(userId)) {
      setUsersIds(usersIds.filter(id => id !== userId));
    } else {
      setUsersIds([...usersIds, userId]);
    }
  };

  const handleClose = () => {
    setOpen(false);
    setUsersIds([]);
  };

  const submitAddUsers = async () => {
    await onAddUsersTeam?.(teamId, usersIds);
    // The players picker is server-paginated, so newly added users may not be
    // in the Redux users map yet; without this refresh the team players list
    // (getTeamUsers) silently drops them until the drawer is reopened.
    await dispatch(fetchTeamPlayers(teamId));
    handleClose();
  };

  // Headers
  const elements: SelectListPickerElements<PlayerOutput> = useMemo(() => ({
    icon: { value: () => <PersonOutlined /> },
    headers: [
      {
        field: 'user_email',
        label: 'Name',
        isSortable: true,
        value: (user: PlayerOutput) => resolveUserName(user),
        width: 50,
      },
      {
        field: 'user_organization_name',
        label: 'Organization',
        value: (user: PlayerOutput) =>
          (user.user_organization ? (organizationsMap[user.user_organization]?.organization_name ?? '-') : '-'),
        width: 25,
      },
      {
        field: 'user_tags',
        label: 'Tags',
        value: (user: PlayerOutput) => <ItemTags variant="list" limit={1} tags={user.user_tags} />,
        width: 25,
      },
    ],
  }), [organizationsMap]);

  // Pagination
  const [players, setPlayers] = useState<PlayerOutput[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));
  const paginationComponent = (
    <PaginationComponentV2
      fetch={searchPlayers}
      searchPaginationInput={searchPaginationInput}
      setContent={setPlayers}
      setLoading={setIsLoading}
      entityPrefix="user"
      availableFilterNames={['user_tags']}
      queryableHelpers={queryableHelpers}
    />
  );

  return (
    <div>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
        <ButtonCreate onClick={() => setOpen(true)} label={t('Add players')} />
      </Can>
      {/* Inline dialog: TeamPlayers itself renders in a drawer (never drawer over drawer). */}
      <SelectListPicker<PlayerOutput>
        open={open}
        onClose={handleClose}
        onSubmit={submitAddUsers}
        title={t('Add players')}
        submitLabel={t('Add')}
        inline
        headerComponent={paginationComponent}
        values={players}
        elements={elements}
        sortHelpers={queryableHelpers.sortHelpers}
        selectedIds={usersIds}
        lockedIds={addedUsersIds}
        onToggle={toggleUser}
        getId={element => element.user_id}
        isLoading={isLoading}
        buttonComponent={(
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
            <CreatePlayer
              inline
              onCreate={(user: User) => {
                setPlayers(prev => [user as unknown as PlayerOutput, ...prev]);
                setUsersIds(prev => [...prev, user.user_id]);
              }}
            />
          </Can>
        )}
      />
    </div>
  );
};

export default TeamAddPlayers;
