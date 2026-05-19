import { Button, Dialog as MuiDialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';

import { type OrganizationHelper, type UserHelper } from '../../../../actions/helper';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { deletePlayer } from '../../../../actions/users/User';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { TeamContext } from '../../common/Context';
import { type UserStore } from './Player';

interface PlayerPopoverProps {
  user: UserStore;
  teamId?: string;
  onDelete?: (result: string) => void;
}

const PlayerPopover: FunctionComponent<PlayerPopoverProps> = ({
  user,
  teamId,
  onDelete,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const { currentUser } = useHelper(
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
  const [openRemove, setOpenRemove] = useState(false);

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

  // Button Popover
  const entries = [];
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
          <Button onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </MuiDialog>
    </div>
  );
};

export default PlayerPopover;
