import { MoreVert } from '@mui/icons-material';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  Menu,
  MenuItem,
} from '@mui/material';
import { type FunctionComponent, type MouseEvent, useContext, useState } from 'react';

import { deleteChallenge, updateChallenge } from '../../../../actions/challenge-action';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type Challenge, type ChallengeInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext, Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ChallengeForm from './ChallengeForm';

interface Props {
  challenge: Challenge;
  onRemoveChallenge?: (challengeId: string) => void;
  inline?: boolean;
  disabled?: boolean;
}

const ChallengePopover: FunctionComponent<Props> = ({ challenge, onRemoveChallenge, inline = false, disabled = false }) => {
  // utils
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  // states
  const [openDelete, setOpenDelete] = useState(false);
  const [openRemove, setOpenRemove] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  // popover management
  const handlePopoverOpen = (event: MouseEvent<HTMLElement>) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };
  const handlePopoverClose = () => setAnchorEl(null);

  // Edit action
  const handleOpenEdit = () => {
    setOpenEdit(true);
    handlePopoverClose();
  };
  const handleCloseEdit = () => setOpenEdit(false);
  const onSubmitEdit = (data: ChallengeInput) => {
    return dispatch(updateChallenge(challenge.challenge_id, data)).then(
      () => handleCloseEdit(),
    );
  };

  // Delete action
  const handleOpenDelete = () => {
    setOpenDelete(true);
    handlePopoverClose();
  };
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    dispatch(deleteChallenge(challenge.challenge_id)).then(() => handleCloseDelete());
  };

  // Remove action
  const handleOpenRemove = () => {
    setOpenRemove(true);
    handlePopoverClose();
  };
  const handleCloseRemove = () => setOpenRemove(false);
  const submitRemove = () => {
    onRemoveChallenge?.(challenge.challenge_id);
    handleCloseRemove();
  };

  // Rendering
  const initialValues: Partial<ChallengeInput> = {
    challenge_name: challenge.challenge_name,
    challenge_category: challenge.challenge_category ?? '',
    challenge_content: challenge.challenge_content ?? '',
    challenge_score: challenge.challenge_score,
    challenge_max_attempts: challenge.challenge_max_attempts,
    challenge_tags: challenge.challenge_tags ?? [],
    challenge_documents: challenge.challenge_documents ?? [],
    challenge_flags: challenge.challenge_flags.map(flag => ({
      flag_type: flag.flag_type ?? 'VALUE',
      flag_value: flag.flag_value ?? '',
    })),
  };

  const challengeForm = (
    <ChallengeForm
      challengeId={challenge.challenge_id}
      editing
      onSubmit={onSubmitEdit}
      handleClose={handleCloseEdit}
      initialValues={initialValues}
    />
  );

  return (
    <>
      {(ability.can(ACTIONS.MANAGE, SUBJECTS.CHALLENGES) || ability.can(ACTIONS.DELETE, SUBJECTS.CHALLENGES) || onRemoveChallenge) && (
        <IconButton disabled={disabled} onClick={handlePopoverOpen} aria-haspopup="true" size="small" color="primary" sx={{ borderRadius: 1 }}>
          <MoreVert fontSize="small" />
        </IconButton>
      )}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handlePopoverClose}
      >
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.CHALLENGES}>
          <MenuItem onClick={handleOpenEdit}>{t('Update')}</MenuItem>
        </Can>
        {onRemoveChallenge && (
          <MenuItem onClick={handleOpenRemove}>
            {t('Remove from the inject')}
          </MenuItem>
        )}
        <Can I={ACTIONS.DELETE} a={SUBJECTS.CHALLENGES}>
          <MenuItem onClick={handleOpenDelete}>{t('Delete')}</MenuItem>
        </Can>
      </Menu>
      <Dialog
        open={openDelete}
        slots={{ transition: Transition }}
        onClose={handleCloseDelete}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this challenge?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button variant="contained" color="error" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>

      {inline ? (
        <Dialog
          open={openEdit}
          slots={{ transition: Transition }}
          onClose={handleCloseEdit}
          fullWidth
          maxWidth="md"
          slotProps={{ paper: { elevation: 1 } }}
        >
          <DialogTitle>{t('Update the challenge')}</DialogTitle>
          <DialogContent>
            {challengeForm}
          </DialogContent>
        </Dialog>
      ) : (
        <Drawer
          open={openEdit}
          handleClose={handleCloseEdit}
          title={t('Update the challenge')}
        >
          {challengeForm}
        </Drawer>
      )}

      <Dialog
        open={openRemove}
        slots={{ transition: Transition }}
        onClose={handleCloseRemove}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to remove this challenge from the inject?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button variant="contained" color="primary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ChallengePopover;
