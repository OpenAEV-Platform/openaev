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
import * as R from 'ramda';
import { useContext, useState } from 'react';
import { useDispatch } from 'react-redux';

import { deleteChallenge, updateChallenge } from '../../../../actions/challenge-action.js';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { tagOptions } from '../../../../utils/Option';
import { AbilityContext, Can } from '../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types.js';
import ChallengeForm from './ChallengeForm';

const ChallengePopover = ({ challenge, onRemoveChallenge, inline, disabled = false }) => {
  // utils

  const dispatch = useDispatch();
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  // states
  const [openDelete, setOpenDelete] = useState(false);
  const [openRemove, setOpenRemove] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);

  // popover management
  const { tagsMap } = useHelper(helper => ({ tagsMap: helper.getTagsMap() }));
  const handlePopoverOpen = (event) => {
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
  const onSubmitEdit = (data) => {
    const inputValues = R.pipe(
      R.assoc('challenge_tags', R.pluck('id', data.challenge_tags)),
    )(data);
    return dispatch(updateChallenge(challenge.challenge_id, inputValues)).then(
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
  const handleOpenRemove = () => {
    setOpenRemove(true);
    handlePopoverClose();
  };
  const handleCloseRemove = () => {
    setOpenRemove(false);
  };
  const submitRemove = () => {
    onRemoveChallenge(challenge.challenge_id);
    handleCloseRemove();
  };

  // Rendering
  const challengeTags = tagOptions(challenge.challenge_tags, tagsMap);
  const initialValues = R.pipe(
    R.assoc('challenge_tags', challengeTags),
    R.pick([
      'challenge_name',
      'challenge_category',
      'challenge_content',
      'challenge_score',
      'challenge_tags',
      'challenge_max_attempts',
      'challenge_flags',
    ]),
  )(challenge);
  return (
    <>
      {(ability.can(ACTIONS.MANAGE, SUBJECTS.CHALLENGES) || ability.can(ACTIONS.DELETE, SUBJECTS.CHALLENGES) || onRemoveChallenge) && (
        <IconButton disabled={disabled} onClick={handlePopoverOpen} aria-haspopup="true" size="large" color="primary">
          <MoreVert />
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
        TransitionComponent={Transition}
        onClose={handleCloseDelete}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this challenge?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>

      {inline ? (
        <Dialog
          open={openEdit}
          TransitionComponent={Transition}
          onClose={handleCloseEdit}
          fullWidth
          maxWidth="md"
          PaperProps={{ elevation: 1 }}
        >
          <DialogTitle>{t('Update the challenge')}</DialogTitle>
          <DialogContent>
            <ChallengeForm
              challengeId={challenge.challenge_id}
              editing
              onSubmit={onSubmitEdit}
              handleClose={handleCloseEdit}
              initialValues={initialValues}
              documentsIds={challenge.challenge_documents || []}
            />
          </DialogContent>
        </Dialog>
      ) : (
        <Drawer
          open={openEdit}
          handleClose={handleCloseEdit}
          title={t('Update the challenge')}
        >
          <ChallengeForm
            challengeId={challenge.challenge_id}
            editing
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
            initialValues={initialValues}
            documentsIds={challenge.challenge_documents || []}
          />
        </Drawer>
      )}

      <Dialog
        open={openRemove}
        TransitionComponent={Transition}
        onClose={handleCloseRemove}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to remove this challenge from the inject?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ChallengePopover;
