import { GroupsOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { Drawer, List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { type CSSProperties, useContext, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchTeams } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { useHelper } from '../../../../store';
import { type SearchPaginationInput, type Team } from '../../../../utils/api-types';
import { AbilityContext, Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateTeam from './CreateTeam';
import TeamPlayers from './TeamPlayers';
import TeamPopover from './TeamPopover';

const useStyles = makeStyles()(() => ({
  itemHead: {
    textTransform: 'uppercase',
    cursor: 'pointer',
    paddingLeft: 10,
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  drawerPaper: {
    minHeight: '100vh',
    width: '50%',
    padding: 0,
  },
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
  const headers = [
    {
      field: 'team_name',
      label: 'Name',
      isSortable: true,
    },
    {
      field: 'team_description',
      label: 'Description',
      isSortable: true,
    },
    {
      field: 'team_users_number',
      label: 'Players',
      isSortable: false,
    },
    {
      field: 'team_tags',
      label: 'Tags',
      isSortable: false,
    },
    {
      field: 'team_updated_at',
      label: 'Updated',
      isSortable: true,
    },
  ];

  const [teams, setTeams] = useState<Team[]>([]);
  const [searchPaginationInput, setSearchPaginationInput] = useState<SearchPaginationInput>(buildSearchPagination({
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

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Teams') }, {
          label: t('Teams of players'),
          current: true,
        }]}
      />
      <PaginationComponent
        fetch={searchTeamsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setTeams}
        exportProps={exportProps}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
        >
          <ListItemIcon />
          <ListItemText
            primary={(
              <SortHeadersComponent
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                searchPaginationInput={searchPaginationInput}
                setSearchPaginationInput={setSearchPaginationInput}
              />
            )}
          />
          <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : teams.map((team: Team) => (
              <ListItem
                key={team.team_id}
                classes={{ root: classes.item }}
                divider
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
                <ListItemIcon>
                  <GroupsOutlined color="primary" />
                </ListItemIcon>
                <ListItemText
                  primary={(
                    <div style={bodyItemsStyles.bodyItems}>
                      <div style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.team_name,
                      }}
                      >
                        {team.team_name}
                      </div>
                      <div style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.team_description,
                      }}
                      >
                        {team.team_description || '-'}
                      </div>
                      <div style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.team_users_number,
                      }}
                      >
                        {team.team_users_number}
                      </div>
                      <div style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.team_tags,
                      }}
                      >
                        <ItemTags variant="list" tags={team.team_tags} />
                      </div>
                      <div style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.team_updated_at,
                      }}
                      >
                        {nsdt(team.team_updated_at)}
                      </div>
                    </div>
                  )}
                />
              </ListItem>
            ))}
      </List>
      <Drawer
        open={selectedTeam !== null}
        keepMounted={false}
        anchor="right"
        sx={{ zIndex: 1202 }}
        classes={{ paper: classes.drawerPaper }}
        onClose={() => onPlayersChanged(selectedTeam)}
        elevation={1}
      >
        {selectedTeam !== null && (
          <TeamPlayers
            teamId={selectedTeam}
            handleClose={() => onPlayersChanged(selectedTeam)}
            canManage={ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS)}
          />
        )}
      </Drawer>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
        <CreateTeam onCreate={result => setTeams([result, ...teams])} />
      </Can>
    </>
  );
};

export default Teams;
