import { CheckCircleOutlined, GroupsOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type Header } from '../../../../components/common/SortHeadersList';
import ItemTags from '../../../../components/ItemTags';
import { type TeamOutput } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { PermissionsContext, TeamContext } from '../../common/Context';
import TeamPlayers from './TeamPlayers';
import TeamPopover from './TeamPopover';

const useStyles = makeStyles()(() => ({
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  bodyItem: {
    height: 20,
    fontSize: 13,
    float: 'left',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  drawerPaper: {
    minHeight: '100vh',
    width: '50%',
    padding: 0,
  },
}));

const inlineStylesContextual: Record<string, CSSProperties> = {
  team_name: {
    float: 'left',
    width: '35%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  team_users_number: {
    float: 'left',
    width: '10%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  team_users_enabled_number: {
    float: 'left',
    width: '10%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  team_tags: {
    float: 'left',
    width: '29%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  team_contextual: {
    float: 'left',
    width: '8%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

const ContextualTeams: FunctionComponent<{ reloadContentCount?: number }> = ({ reloadContentCount = 0 }) => {
  // Standard hooks
  const { classes } = useStyles();
  const [selectedTeam, setSelectedTeam] = useState<string | null>(null);
  const { computeTeamUsersEnabled, searchTeams } = useContext(TeamContext);
  const { permissions } = useContext(PermissionsContext);
  const ability = useContext(AbilityContext);

  const [teams, setTeams] = useState<TeamOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [localReloadCount, setLocalReloadCount] = useState(0);

  // Pagination
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({
    sorts: initSorting('team_name'),
  }));

  // Headers
  const headers: Header[] = [
    { field: 'team_name', label: 'Name', isSortable: true },
    { field: 'team_users_number', label: 'Players', isSortable: false },
    ...(computeTeamUsersEnabled ? [{ field: 'team_users_enabled_number', label: 'Enabled', isSortable: false }] : []),
    { field: 'team_tags', label: 'Tags', isSortable: false },
    { field: 'team_contextual', label: 'Contextual', isSortable: false },
  ];

  return (
    <>
      <div className="clearfix" />
      <PaginationComponentV2
        fetch={input => searchTeams(input, true)}
        searchPaginationInput={searchPaginationInput}
        setContent={setTeams}
        setLoading={setLoading}
        entityPrefix="team"
        availableFilterNames={[]}
        queryableHelpers={queryableHelpers}
        reloadContentCount={reloadContentCount + localReloadCount}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
          secondaryAction={<></>}
        >
          <ListItemIcon>
            <span style={{ padding: '0 8px 0 10px', fontWeight: 700, fontSize: 12 }}>#</span>
          </ListItemIcon>
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={headers}
                inlineStylesHeaders={inlineStylesContextual}
                sortHelpers={queryableHelpers.sortHelpers}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStylesContextual} />
          : teams.map((team: TeamOutput) => (
              <ListItem
                key={team.team_id}
                disablePadding
                secondaryAction={
                  permissions.canManage && (
                    <TeamPopover
                      team={team}
                      managePlayers={() => setSelectedTeam(team.team_id)}
                      onUpdate={() => setLocalReloadCount(c => c + 1)}
                      onDelete={() => setLocalReloadCount(c => c + 1)}
                      onTeamRemoved={() => setLocalReloadCount(c => c + 1)}
                    />
                  )
                }
              >
                <ListItemButton
                  classes={{ root: classes.item }}
                  divider
                  onClick={() => setSelectedTeam(team.team_id)}
                >
                  <ListItemIcon>
                    <GroupsOutlined color="primary" />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <>
                        <div className={classes.bodyItem} style={inlineStylesContextual.team_name}>
                          {team.team_name}
                        </div>
                        <div className={classes.bodyItem} style={inlineStylesContextual.team_users_number}>
                          {team.team_users_number}
                        </div>
                        {computeTeamUsersEnabled && (
                          <div className={classes.bodyItem} style={inlineStylesContextual.team_users_enabled_number}>
                            {computeTeamUsersEnabled(team.team_id)}
                          </div>
                        )}
                        <div className={classes.bodyItem} style={inlineStylesContextual.team_tags}>
                          <ItemTags variant="reduced-view" tags={team.team_tags} />
                        </div>
                        <div className={classes.bodyItem} style={inlineStylesContextual.team_contextual}>
                          {team.team_contextual ? <CheckCircleOutlined fontSize="small" /> : '-'}
                        </div>
                      </>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))}
      </List>
      <Drawer
        open={selectedTeam !== null}
        keepMounted={false}
        anchor="right"
        sx={{ zIndex: 1202 }}
        classes={{ paper: classes.drawerPaper }}
        onClose={() => {
          setSelectedTeam(null);
          setLocalReloadCount(c => c + 1);
        }}
        elevation={1}
      >
        {selectedTeam !== null && (
          <TeamPlayers
            teamId={selectedTeam}
            handleClose={() => {
              setSelectedTeam(null);
              setLocalReloadCount(c => c + 1);
            }}
            canManage={permissions.canManage && ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS)}
          />
        )}
      </Drawer>
    </>
  );
};

export default ContextualTeams;
