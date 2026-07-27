import { GroupsOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { Box, Checkbox, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useContext, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { bulkDeleteTeams, searchTeams } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import ExportButton from '../../../../components/common/ExportButton';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { TEAM_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type SearchPaginationInput, type Team } from '../../../../utils/api-types';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import { AbilityContext, Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ToolBar from '../../common/ToolBar';
import CreateTeam from './CreateTeam';
import TeamPlayers from './TeamPlayers';
import TeamPopover from './TeamPopover';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  team_name: { width: '25%' },
  team_description: { width: '20%' },
  team_users_number: {
    width: '10%',
    cursor: 'default',
  },
  team_tags: {
    width: '25%',
    cursor: 'default',
  },
  team_updated_at: { width: '20%' },
};

const Teams = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const ability = useContext(AbilityContext);

  const [selectedTeam, setSelectedTeam] = useState<string | null>(null);

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchId] = searchParams.getAll('id');

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'team_name',
      label: 'Name',
      isSortable: true,
      value: (team: Team) => team.team_name,
    },
    {
      field: 'team_description',
      label: 'Description',
      isSortable: true,
      value: (team: Team) => team.team_description || '-',
    },
    {
      field: 'team_users_number',
      label: 'Players',
      isSortable: false,
      value: (team: Team) => String(team.team_users_number ?? 0),
    },
    {
      field: 'team_tags',
      label: 'Tags',
      isSortable: false,
      value: (team: Team) => <ItemTags variant="list" tags={team.team_tags} />,
    },
    {
      field: 'team_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (team: Team) => nsdt(team.team_updated_at),
    },
  ], [nsdt]);

  const availableFilterNames = [
    'team_name',
    'team_tags',
  ];

  const [teams, setTeams] = useState<Team[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('teams', buildSearchPagination({
    sorts: initSorting('team_name'),
    textSearch: search,
  }));

  const { refetched } = useHelper((helper: TeamsHelper) => ({ refetched: helper.getTeam(selectedTeam ?? '') }));

  const onTeamUpdated = (team: Team) => {
    setTeams(teams.map(v => (v.team_id !== team.team_id ? v : team)));
  };

  const onPlayersChanged = (team_id: string | null) => {
    if (team_id) {
      onTeamUpdated(refetched);
      setSelectedTeam(null);
    }
  };

  // Export
  const exportProps = {
    exportType: 'team',
    exportKeys: [
      'team_name',
      'team_description',
      'team_users_number',
      'team_enabled',
      'team_tags',
    ],
    exportData: teams,
    exportFileName: `${t('Teams')}.csv`,
  };

  const [loading, setLoading] = useState<boolean>(true);
  const searchTeamsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchTeams(input).finally(() => setLoading(false));
  };

  // Bulk selection
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS);
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<Team>('team', teams, queryableHelpers.paginationHelpers.getTotalElements());

  const bulkDelete = () => {
    bulkDeleteTeams({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      team_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      team_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setTeams(teams.filter(team => !deletedIds.includes(team.team_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Teams'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={searchTeamsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setTeams}
        entityPrefix="team"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
              <CreateTeam onCreate={result => setTeams([result, ...teams])} />
            </Can>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          sx={numberOfSelectedElements > 0
            ? {
                // Massive-operations toolbar: symmetric vertical padding keeps the
                // checkbox and actions vertically centered in the accent band.
                backgroundColor: 'background.accent',
                paddingBlock: 0.5,
              }
            : { paddingTop: 0 }}
          {...(numberOfSelectedElements === 0 ? { secondaryAction: <>&nbsp;</> } : {})}
        >
          {canManage && (
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
              />
            </ListItemIcon>
          )}
          {numberOfSelectedElements > 0 ? (
            <ListItemText
              primary={(
                <ToolBar
                  numberOfSelectedElements={numberOfSelectedElements}
                  handleClearSelectedElements={handleClearSelectedElements}
                  handleBulkDelete={bulkDelete}
                  canManage={canManage}
                  deleteConfirmationSingular={t('Do you want to delete this team?')}
                  deleteConfirmationPlural={t('Do you want to delete these {count} teams?', { count: String(numberOfSelectedElements) })}
                />
              )}
            />
          ) : (
            <>
              <ListItemIcon />
              <ListItemText
                primary={(
                  <SortHeadersComponentV2
                    headers={headers}
                    inlineStylesHeaders={inlineStyles}
                    sortHelpers={queryableHelpers.sortHelpers}
                  />
                )}
              />
            </>
          )}
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox={canManage} />
          : teams.map((team: Team) => (
              <ListItem
                key={team.team_id}
                divider
                disablePadding
                secondaryAction={(
                  <TeamPopover
                    team={team}
                    managePlayers={() => setSelectedTeam(team.team_id)}
                    onUpdate={result => onTeamUpdated(result)}
                    onDelete={result => setTeams(teams.filter(v => (v.team_id !== result)))}
                    openEditOnInit={team.team_id === searchId}
                  />
                )}
              >
                <ListItemButton classes={{ root: classes.item }} component={Link} to={`${TEAM_BASE_URL}/${team.team_id}`}>
                  {canManage && (
                    <ListItemIcon
                      style={{ minWidth: 40 }}
                      onClick={event => onToggleEntity(team, event)}
                    >
                      <Checkbox
                        edge="start"
                        checked={
                          (selectAll && !(team.team_id in (deSelectedElements || {})))
                          || team.team_id in (selectedElements || {})
                        }
                        disableRipple
                      />
                    </ListItemIcon>
                  )}
                  <ListItemIcon>
                    <GroupsOutlined color="primary" />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        {headers.map(header => (
                          <div
                            key={header.field}
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles[header.field],
                            }}
                          >
                            {header.value?.(team)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))}
      </List>
      {selectedTeam !== null && (
        <TeamPlayers
          teamId={selectedTeam}
          handleClose={() => onPlayersChanged(selectedTeam)}
          canManage={ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS)}
        />
      )}
    </>
  );
};

export default Teams;
