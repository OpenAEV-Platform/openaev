import { Add, PersonOutlined } from '@mui/icons-material';
import {
  Avatar,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Fab,
  Grid,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type OrganizationHelper, type UserHelper } from '../../../../actions/helper';
import { fetchPlayers } from '../../../../actions/User';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type Organization, type Team, type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { type Option } from '../../../../utils/Option';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { resolveUserName, truncate } from '../../../../utils/String';
import { TeamContext } from '../../common/Context';
import TagsFilter from '../../common/filters/TagsFilter';
import CreatePlayer from '../../teams/players/CreatePlayer';
import { type UserStore } from '../../teams/players/Player';

const useStyles = makeStyles()(() => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 30,
  },
  box: {
    width: '100%',
    minHeight: '100%',
    padding: 20,
    border: '1px dashed rgba(255, 255, 255, 0.3)',
  },
  chip: { margin: '0 10px 10px 0' },
}));

interface Props {
  addedUsersIds: UserStore['user_id'][];
  teamId: Team['team_id'];
}

type UserStoreExtended = UserStore & {
  organization_name: Organization['organization_name'];
  organization_description: Organization['organization_description'];
};

const TeamAddPlayers: FunctionComponent<Props> = ({ addedUsersIds, teamId }) => {
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const { classes } = useStyles();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [usersIds, setUsersIds] = useState<UserStore['user_id'][]>([]);
  const [tags, setTags] = useState<Option[]>([]);

  const { onAddUsersTeam } = useContext(TeamContext);

  const { usersMap, organizationsMap }: {
    organizationsMap: Record<string, Organization>;
    usersMap: Record<string, UserStore>;
  } = useHelper((helper: UserHelper & OrganizationHelper) => ({
    usersMap: helper.getUsersMap(),
    organizationsMap: helper.getOrganizationsMap(),
  }));

  useDataLoader(() => {
    dispatch(fetchPlayers());
  });

  const filterByKeyword = (n: UserStoreExtended) => keyword === ''
    || (n.user_email || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (n.user_firstname || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (n.user_lastname || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (n.user_phone || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (n.organization_name || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1
    || (n.organization_description || '').toLowerCase().indexOf(keyword.toLowerCase()) !== -1;
  const filteredUsers = R.pipe(
    R.map((u: UserStore) => ({
      organization_name:
        u.user_organization ? (organizationsMap[u.user_organization]?.organization_name ?? '-') : '-',
      organization_description:
        u.user_organization
          ? (organizationsMap[u.user_organization]?.organization_description
            ?? '-')
          : '-',
      ...u,
    })),
    R.filter(
      (n: UserStoreExtended) => tags.length === 0
        || R.any(
          (filter: Option['id']) => R.includes(filter, n.user_tags),
          R.pluck('id', tags),
        ),
    ),
    R.filter(filterByKeyword),
    R.take(10),
  )(R.values(usersMap));

  const submitAddUsers = async () => {
    await onAddUsersTeam?.(teamId, usersIds);
    setOpen(false);
    setKeyword('');
    setUsersIds([]);
  };

  return (
    <div>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
        <Fab
          onClick={() => setOpen(true)}
          color="primary"
          aria-label="Add"
          className={classes.createButton}
        >
          <Add />
        </Fab>
      </Can>
      <Dialog
        open={open}
        TransitionComponent={Transition}
        onClose={() => {
          setOpen(false);
          setKeyword('');
          setUsersIds([]);
        }}
        fullWidth
        maxWidth="lg"
        PaperProps={{
          elevation: 1,
          sx: {
            minHeight: 580,
            maxHeight: 580,
          },
        }}
      >
        <DialogTitle>{t('Add players in this team')}</DialogTitle>
        <DialogContent>
          <Grid container spacing={3}>
            <Grid size={{ xs: 8 }}>
              <Grid container spacing={3}>
                <Grid size={{ xs: 6 }}>
                  <SearchFilter
                    onChange={(value?: string) => setKeyword(value || '')}
                    fullWidth
                  />
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <TagsFilter
                    onAddTag={(value: Option) => {
                      if (value) {
                        setTags([value]);
                      }
                    }}
                    onClearTag={() => setTags([])}
                    currentTags={tags}
                    fullWidth
                  />
                </Grid>
              </Grid>
              <List>
                {filteredUsers.map((user: UserStoreExtended) => {
                  const disabled = usersIds.includes(user.user_id)
                    || addedUsersIds.includes(user.user_id);
                  return (
                    (
                      <ListItemButton
                        key={user.user_id}
                        disabled={disabled}
                        divider
                        dense
                        onClick={() => setUsersIds([...usersIds, user.user_id])}
                      >
                        <ListItemIcon>
                          <PersonOutlined />
                        </ListItemIcon>
                        <ListItemText
                          primary={resolveUserName(user)}
                          secondary={user.organization_name}
                        />
                        <ItemTags variant="reduced-view" tags={user.user_tags} />
                      </ListItemButton>
                    )
                  );
                })}
                <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
                  <CreatePlayer
                    inline
                    onCreate={(user: User) => {
                      setUsersIds([...usersIds, user.user_id]);
                      dispatch(fetchPlayers());
                    }}
                  />
                </Can>
              </List>
            </Grid>
            <Grid size={{ xs: 4 }}>
              <Box className={classes.box}>
                {usersIds.map((userId) => {
                  const user = usersMap[userId];
                  const userGravatar = R.propOr('-', 'user_gravatar', user);
                  return (
                    <Chip
                      key={userId}
                      onDelete={() => {
                        setUsersIds(usersIds.filter(id => id !== userId));
                      }}
                      label={user && truncate(resolveUserName(user), 22)}
                      avatar={(
                        <Avatar
                          src={userGravatar}
                          sx={{
                            height: '32px',
                            width: '32px',
                          }}
                        />
                      )}
                      classes={{ root: classes.chip }}
                    />
                  );
                })}
              </Box>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setOpen(false);
            setKeyword('');
            setUsersIds([]);
          }}
          >
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitAddUsers}>
            {t('Add')}
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
};

export default TeamAddPlayers;
