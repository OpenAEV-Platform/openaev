import { CheckCircleOutlined, GroupsOutlined } from '@mui/icons-material';
import { Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import ItemTags from '../../../../components/ItemTags';
import { type Team } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useSearchAnFilter from '../../../../utils/SortingFiltering';
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

const headerStylesContextual: Record<string, CSSProperties> = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  team_name: {
    float: 'left',
    width: '35%',
    fontSize: 12,
    fontWeight: '700',
  },
  team_users_number: {
    float: 'left',
    width: '10%',
    fontSize: 12,
    fontWeight: '700',
  },
  team_users_enabled_number: {
    float: 'left',
    width: '10%',
    fontSize: 12,
    fontWeight: '700',
  },
  team_tags: {
    float: 'left',
    width: '29%',
    fontSize: 12,
    fontWeight: '700',
  },
  team_contextual: {
    float: 'left',
    width: '8%',
    fontSize: 12,
    fontWeight: '700',
  },
};

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

interface Props { teams: Team[] }

interface TeamStoreExtended extends Team { team_users_enabled_number: number }

const ContextualTeams: FunctionComponent<Props> = ({ teams }) => {
  // Standard hooks
  const { classes } = useStyles();
  const [selectedTeam, setSelectedTeam] = useState<string | null>(null);
  const { computeTeamUsersEnabled } = useContext(TeamContext);
  const { permissions } = useContext(PermissionsContext);
  const ability = useContext(AbilityContext);

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchId] = searchParams.getAll('id');

  // Filter and sort hook
  const filtering = useSearchAnFilter(
    'team',
    'name',
    [
      'name',
      'description',
    ],
    { defaultKeyword: search },
  );
  const sortedTeams = filtering.filterAndSort(teams.map((team) => {
    if (computeTeamUsersEnabled) {
      return ({
        team_users_enabled_number: computeTeamUsersEnabled(team.team_id),
        ...team,
      });
    }
    return team;
  }));
  return (
    <>
      <div className="clearfix" />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
          secondaryAction={<></>}
        >
          <ListItemIcon>
            <span
              style={{
                padding: '0 8px 0 10px',
                fontWeight: 700,
                fontSize: 12,
              }}
            >
              #
            </span>
          </ListItemIcon>
          <ListItemText
            primary={(
              <>
                {filtering.buildHeader(
                  'team_name',
                  'Name',
                  true,
                  headerStylesContextual,
                )}
                {filtering.buildHeader(
                  'team_users_number',
                  'Players',
                  true,
                  headerStylesContextual,
                )}
                {computeTeamUsersEnabled && filtering.buildHeader(
                  'team_users_enabled_number',
                  'Enabled',
                  true,
                  headerStylesContextual,
                )}
                {filtering.buildHeader(
                  'team_tags',
                  'Tags',
                  true,
                  headerStylesContextual,
                )}
                {filtering.buildHeader(
                  'team_contextual',
                  'Contextual',
                  true,
                  headerStylesContextual,
                )}
              </>
            )}
          />
        </ListItem>
        {sortedTeams.map((team: TeamStoreExtended) => (
          <ListItem
            key={team.team_id}
            disablePadding
            secondaryAction={(
              permissions.canManage
              && (
                <TeamPopover
                  team={team}
                  managePlayers={() => setSelectedTeam(team.team_id)}
                  openEditOnInit={team.team_id === searchId}
                />
              )
            )}
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
                    <div
                      className={classes.bodyItem}
                      style={inlineStylesContextual.team_name}
                    >
                      {team.team_name}
                    </div>
                    <div
                      className={classes.bodyItem}
                      style={inlineStylesContextual.team_users_number}
                    >
                      {team.team_users_number}
                    </div>
                    {computeTeamUsersEnabled && (
                      <div
                        className={classes.bodyItem}
                        style={inlineStylesContextual.team_users_enabled_number}
                      >
                        {team.team_users_enabled_number}
                      </div>
                    )}
                    <div
                      className={classes.bodyItem}
                      style={inlineStylesContextual.team_tags}
                    >
                      <ItemTags variant="reduced-view" tags={team.team_tags} />
                    </div>
                    <div
                      className={classes.bodyItem}
                      style={inlineStylesContextual.team_contextual}
                    >
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
        onClose={() => setSelectedTeam(null)}
        elevation={1}
      >
        {selectedTeam !== null && (
          <TeamPlayers
            teamId={selectedTeam}
            handleClose={() => setSelectedTeam(null)}
            canManage={permissions.canManage && ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS)}
          />
        )}
      </Drawer>
    </>
  );
};

export default ContextualTeams;
