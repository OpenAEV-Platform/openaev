import { ArrowDropDownOutlined, ArrowDropUpOutlined, EmailOutlined, KeyOutlined, PersonOutlined, SmartphoneOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type OrganizationHelper, type UserHelper } from '../../../../actions/helper';
import { fetchOrganizations } from '../../../../actions/Organization';
import { fetchTeam, fetchTeamPlayers } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import ItemBoolean from '../../../../components/ItemBoolean';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type Organization, type Team } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { isEmpty } from '../../../../utils/object';
import { type Option } from '../../../../utils/Option';
import { ascendBy, descendBy } from '../../../../utils/Sorting';
import { TeamContext } from '../../common/Context';
import TagsFilter from '../../common/filters/TagsFilter';
import { type UserStore } from '../../teams/players/Player';
import PlayerPopover from '../../teams/players/PlayerPopover';
import TeamAddPlayers from './TeamAddPlayers';

const useStyles = makeStyles()(() => ({
  container: { marginTop: 10 },
  itemHead: {
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: { height: 50 },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
  icon: { marginRight: 10 },
}));

const inlineStylesHeaders: Record<string, CSSProperties> = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  user_enabled: {
    float: 'left',
    width: '12%',
    fontSize: 12,
    fontWeight: '400',
    color: 'var(--text-default-secondary)',
  },
  user_email: {
    float: 'left',
    width: '30%',
    fontSize: 12,
    fontWeight: '400',
    color: 'var(--text-default-secondary)',
  },
  user_options: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '400',
    color: 'var(--text-default-secondary)',
  },
  user_organization: {
    float: 'left',
    width: '18%',
    fontSize: 12,
    fontWeight: '400',
    color: 'var(--text-default-secondary)',
  },
  user_tags: {
    float: 'left',
    width: '25%',
    fontSize: 12,
    fontWeight: '400',
    color: 'var(--text-default-secondary)',

  },
};

const inlineStyles: Record<string, CSSProperties> = {
  user_enabled: {
    float: 'left',
    width: '11%',
    marginRight: '1%',
    minHeight: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  clickable: { cursor: 'pointer' },
  user_email: {
    float: 'left',
    width: '30%',
    minHeight: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  user_options: {
    float: 'left',
    width: '15%',
    minHeight: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  user_organization: {
    float: 'left',
    width: '18%',
    minHeight: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  user_tags: {
    float: 'left',
    width: '25%',
    minHeight: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

interface Props {
  teamId: Team['team_id'];
  handleClose: () => void;
  canManage: boolean;
  /** Notified when a player is updated or deleted from the drawer, so the host page can refresh its own lists. */
  onPlayersChange?: () => void;
}

type UserStoreExtended = UserStore & { user_enabled: boolean };

const TeamPlayers: FunctionComponent<Props> = ({ teamId, handleClose, canManage, onPlayersChange }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [keyword, setKeyword] = useState('');
  const [sortBy, setSortby] = useState('user_email');
  const [orderAsc, setOrderAsc] = useState(true);
  const [tags, setTags] = useState<Option[]>([]);

  const { organizationsMap, team, users }: {
    organizationsMap: Record<string, Organization>;
    team: Team;
    users: UserStore[];
  } = useHelper((helper: UserHelper & TeamsHelper & OrganizationHelper) => ({
    organizationsMap: helper.getOrganizationsMap(),
    team: helper.getTeam(teamId),
    users: helper.getTeamUsers(teamId),
  }));

  const { onToggleUser, checkUserEnabled } = useContext(TeamContext);

  useDataLoader(() => {
    dispatch(fetchTeam(teamId));
    dispatch(fetchTeamPlayers(teamId));
    dispatch(fetchOrganizations());
  });

  const filterByKeyword = (user: UserStore) => keyword === ''
    || (user.user_email || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (user.user_firstname || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (user.user_lastname || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (user.user_phone || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (user.user_organization || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1;
  // The organization column displays the organization name, so it sorts by that name rather than by its id
  const organizationName = (user: UserStoreExtended) => (user.user_organization
    ? organizationsMap?.[user.user_organization]?.organization_name
    : undefined) ?? '';
  const compareByOrganization = (a: UserStoreExtended, b: UserStoreExtended) => organizationName(a).localeCompare(organizationName(b));
  const sortKey = sortBy as keyof UserStoreExtended;
  const compareByKey = orderAsc ? ascendBy<UserStoreExtended>(sortKey) : descendBy<UserStoreExtended>(sortKey);
  const compareUsers = sortBy === 'user_organization'
    ? (a: UserStoreExtended, b: UserStoreExtended) => (orderAsc ? compareByOrganization(a, b) : compareByOrganization(b, a))
    : compareByKey;
  const sortedUsers: UserStoreExtended[] = users
    .filter(u => !!u)
    .filter((user: UserStore) => tags.length === 0
      || tags.map(item => item.id).some((filter: Option['id']) => user.user_tags!.includes(filter))).filter(filterByKeyword)
    .map((user: UserStore) => {
      if (checkUserEnabled) {
        return ({
          user_enabled: checkUserEnabled(teamId, user.user_id),
          ...user,
        });
      }
      return user;
    })
    .sort(compareUsers);

  const sortHeader = (field: string, label: string, isSortable: boolean) => {
    const sortComponent = orderAsc
      ? (
          <ArrowDropDownOutlined style={inlineStylesHeaders.iconSort} />
        )
      : (
          <ArrowDropUpOutlined style={inlineStylesHeaders.iconSort} />
        );
    if (isSortable) {
      return (
        <div
          style={inlineStylesHeaders[field]}
          onClick={() => {
            setSortby(field);
            setOrderAsc(!orderAsc);
          }}
        >
          <span>{t(label)}</span>
          {sortBy === field ? sortComponent : ''}
        </div>
      );
    }
    return (
      <div style={inlineStylesHeaders[field]}>
        <span>{t(label)}</span>
      </div>
    );
  };

  return (
    <Drawer
      open
      handleClose={handleClose}
      title={team?.team_name ?? '-'}
    >
      <>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 1.5,
            marginTop: 1,
          }}
        >
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 1.5,
              minWidth: 0,
            }}
          >
            <SearchFilter
              variant="thin"
              onChange={(value?: string) => setKeyword(value || '')}
              keyword={keyword}
            />
            <TagsFilter
              onAddTag={(value: Option) => {
                if (value) {
                  setTags([...tags, value]);
                }
              }}
              onRemoveTag={(value: Option['id']) => {
                setTags(tags.filter((t: Option) => t.id !== value));
              }}
              currentTags={tags}
            />
          </Box>
          {canManage && (
            <Box sx={{
              alignSelf: 'flex-start',
              flexShrink: 0,
            }}
            >
              <TeamAddPlayers
                teamId={teamId}
                addedUsersIds={users.filter(u => !!u).map(u => u.user_id)}
              />
            </Box>
          )}
        </Box>
        <List classes={{ root: classes.container }}>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<span> &nbsp; </span>}
          >
            <ListItemIcon>
              <span
                style={{
                  padding: '0 8px 0 8px',
                  fontWeight: 700,
                  fontSize: 12,
                }}
              >
                &nbsp;
              </span>
            </ListItemIcon>
            <ListItemText
              primary={(
                <>
                  {onToggleUser && sortHeader('user_enabled', 'Enabled', true)}
                  {sortHeader('user_email', 'Email address', true)}
                  {sortHeader('user_options', 'Options', false)}
                  {sortHeader('user_organization', 'Organization', true)}
                  {sortHeader('user_tags', 'Tags', false)}
                </>
              )}
            />
          </ListItem>
          {sortedUsers.map(user => (
            <ListItem
              key={user.user_id}
              classes={{ root: classes.item }}
              divider
              secondaryAction={canManage
                ? (
                    <PlayerPopover
                      user={user}
                      teamId={teamId}
                      onUpdate={() => onPlayersChange?.()}
                    />
                  )
                : <span> &nbsp; </span>}
            >
              <ListItemIcon>
                <PersonOutlined />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <>
                    {onToggleUser && (
                      <div
                        className={classes.bodyItem}
                        style={{
                          ...inlineStyles.user_enabled,
                          ...(canManage ? inlineStyles.clickable : {}),
                        }}
                        onClick={() => canManage && onToggleUser(teamId, user.user_id, user.user_enabled)}
                      >
                        <ItemBoolean
                          status={user.user_enabled}
                          label={user.user_enabled ? t('Enabled') : t('Disabled')}
                          variant="inList"
                        />
                      </div>
                    )}
                    <div
                      className={classes.bodyItem}
                      style={inlineStyles.user_email}
                    >
                      {user.user_email}
                    </div>
                    <div
                      className={classes.bodyItem}
                      style={inlineStyles.user_options}
                    >
                      {user.user_email === null || user.user_email === undefined
                        || isEmpty(user.user_email) ? (
                            <EmailOutlined
                              color="warning"
                              fontSize="small"
                              className={classes.icon}
                            />
                          ) : (
                            <EmailOutlined
                              color="success"
                              fontSize="small"
                              className={classes.icon}
                            />
                          )}
                      {user.user_pgp_key === null || user.user_pgp_key === undefined
                        || isEmpty(user.user_pgp_key) ? (
                            <KeyOutlined
                              color="warning"
                              fontSize="small"
                              className={classes.icon}
                            />
                          ) : (
                            <KeyOutlined
                              color="success"
                              fontSize="small"
                              className={classes.icon}
                            />
                          )}
                      {user.user_phone === null || user.user_phone === undefined
                        || isEmpty(user.user_phone) ? (
                            <SmartphoneOutlined
                              color="warning"
                              fontSize="small"
                              className={classes.icon}
                            />
                          ) : (
                            <SmartphoneOutlined
                              color="success"
                              fontSize="small"
                              className={classes.icon}
                            />
                          )}
                    </div>
                    <div
                      className={classes.bodyItem}
                      style={inlineStyles.user_organization}
                    >
                      {
                        user.user_organization
                        && organizationsMap?.[user.user_organization]
                          ?.organization_name
                      }
                    </div>
                    <div
                      className={classes.bodyItem}
                      style={inlineStyles.user_tags}
                    >
                      <ItemTags variant="reduced-view" tags={user.user_tags} />
                    </div>
                  </>
                )}
              />
            </ListItem>
          ))}
        </List>
      </>
    </Drawer>
  );
};

export default TeamPlayers;
