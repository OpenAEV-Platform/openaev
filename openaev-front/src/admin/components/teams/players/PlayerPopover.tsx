import { Button, Dialog as MuiDialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';

import { type OrganizationHelper, type UserHelper } from '../../../../actions/helper';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { deletePlayer, updatePlayer } from '../../../../actions/users/User';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type PlayerInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { countryOption, type Option, organizationOption, tagOptions } from '../../../../utils/Option';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { TeamContext } from '../../common/Context';
import { type PlayerInputForm, type UserStore } from './Player';
import PlayerForm from './PlayerForm';

interface PlayerPopoverProps {
  user: UserStore;
  teamId?: string;
  openEditOnInit?: boolean;
  onUpdate?: (result: UserStore) => void;
  onDelete?: (result: string) => void;
}

const PlayerPopover: FunctionComponent<PlayerPopoverProps> = ({
  user,
  teamId,
  openEditOnInit = false,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useAbility();

  const { organizationsMap, tagsMap, currentUser } = useHelper(
    (
      helper: UserHelper & OrganizationHelper & TagHelper,
    ) => {
      return {
        organizationsMap: helper.getOrganizationsMap(),
        tagsMap: helper.getTagsMap(),
        currentUser: helper.getMe(),
      };
    },
  );

  const { onRemoveUsersTeam } = useContext(TeamContext);

  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(openEditOnInit);
  const [openRemove, setOpenRemove] = useState(false);

  // Platform administrators are protected accounts: they are managed from the
  // security settings, so the persons screen restricts what can be done on them.
  const isAdminTarget = user.user_admin === true;
  const currentUserIsAdmin = currentUser?.user_admin === true;

  // Edition
  const handleOpenEdit = () => {
    setOpenEdit(true);
  };

  const handleCloseEdit = () => setOpenEdit(false);

  const onSubmitEdit = (data: PlayerInputForm) => {
    const inputValues: PlayerInput = {
      ...data,
      user_organization: data.user_organization?.id,
      user_country: data.user_country?.id,
      user_tags: data.user_tags?.map((tag: Option) => tag.id),
    };
    return dispatch(updatePlayer(user.user_id, inputValues))
      .then((result: {
        result: string;
        entities: { users: Record<string, UserStore> };
      }) => {
        if (onUpdate) {
          const updated = result.entities.users[result.result];
          onUpdate(updated);
        }
        handleCloseEdit();
      });
  };

  // Deletion
  const handleOpenDelete = () => {
    setOpenDelete(true);
  };

  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    dispatch(deletePlayer(user.user_id))
      .then(
        () => {
          if (onDelete) {
            onDelete(user.user_id);
          }
          handleCloseDelete();
        },
      );
  };

  // Remove
  const handleOpenRemove = () => {
    setOpenRemove(true);
  };

  const handleCloseRemove = () => setOpenRemove(false);

  const submitRemove = async () => {
    await onRemoveUsersTeam?.(teamId!, [user.user_id]);
    handleCloseRemove();
  };

  const initialValues: PlayerInputForm = {
    ...user,
    user_organization: organizationOption(
      user.user_organization,
      organizationsMap,
    ),
    user_country: countryOption(user.user_country),
    user_tags: tagOptions(user.user_tags, tagsMap),
  };

  // Button Popover
  const entries: PopoverEntry[] = [];
  entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS),
    disabled: isAdminTarget && !currentUserIsAdmin,
    disabledMessage: 'Only an administrator can update a platform administrator account.',
  });
  if (teamId) entries.push({
    label: 'Remove from the team',
    action: () => handleOpenRemove(),
    userRight: true,
  });

  // It's not possible to delete your own player
  if (user.user_id !== currentUser.user_id) entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.TEAMS_AND_PLAYERS),
    disabled: isAdminTarget,
    disabledMessage: 'This person is a platform administrator. The account can only be managed from the security settings.',
  });

  return (
    <div>
      <ButtonPopover entries={entries} variant="icon" />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this player?')}
      />
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the player')}
      >
        <PlayerForm
          initialValues={initialValues}
          handleClose={handleCloseEdit}
          onSubmit={onSubmitEdit}
          editing
        />
      </Drawer>
      <MuiDialog
        open={openRemove}
        slots={{ transition: Transition }}
        onClose={handleCloseRemove}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to remove the player from the team?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button variant="contained" color="primary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </MuiDialog>
    </div>
  );
};

export default PlayerPopover;
