import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import * as R from 'ramda';
import { useState } from 'react';
import { useDispatch } from 'react-redux';

import { deleteAttackPattern, updateAttackPattern } from '../../../../actions/AttackPattern';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { killChainPhaseOptions } from '../../../../utils/Option';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import AttackPatternForm from './AttackPatternForm';

const AttackPatternPopover = ({ attackPattern, killChainPhasesMap, onUpdate, onDelete }) => {
  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);
  const dispatch = useDispatch();
  const { t } = useFormatter();
  const handlePopoverOpen = (event) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };
  const handlePopoverClose = () => setAnchorEl(null);
  const handleOpenEdit = () => {
    setOpenEdit(true);
    handlePopoverClose();
  };
  const handleCloseEdit = () => setOpenEdit(false);
  const onSubmitEdit = (data) => {
    const inputValues = R.pipe(
      R.assoc('attack_pattern_kill_chain_phases', R.pluck('id', data.attack_pattern_kill_chain_phases)),
    )(data);
    return dispatch(updateAttackPattern(attackPattern.attack_pattern_id, inputValues)).then((result) => {
      if (onUpdate) {
        const attackPatternUpdated = result.entities.attackpatterns[result.result];
        onUpdate(attackPatternUpdated);
      }
      handleCloseEdit();
    });
  };

  const handleOpenDelete = () => {
    setOpenDelete(true);
    handlePopoverClose();
  };
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    dispatch(deleteAttackPattern(attackPattern.attack_pattern_id)).then(
      () => {
        if (onDelete) {
          onDelete(attackPattern.attack_pattern_id);
        }
      },
    );
    handleCloseDelete();
  };
  const attackPatternKillChainPhases = killChainPhaseOptions(attackPattern.attack_pattern_kill_chain_phases, killChainPhasesMap);
  const initialValues = R.pipe(
    R.pick([
      'attack_pattern_external_id',
      'attack_pattern_name',
      'attack_pattern_description',
    ]),
    R.assoc('attack_pattern_kill_chain_phases', attackPatternKillChainPhases),
  )(attackPattern);
  return (
    <>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
        <IconButton color="primary" onClick={handlePopoverOpen} aria-haspopup="true" size="large">
          <MoreVert />
        </IconButton>
      </Can>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handlePopoverClose}
      >
        <MenuItem onClick={handleOpenEdit}>{t('Update')}</MenuItem>
        <MenuItem onClick={handleOpenDelete}>{t('Delete')}</MenuItem>
      </Menu>
      <Dialog
        open={openDelete}
        TransitionComponent={Transition}
        onClose={handleCloseDelete}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this attack pattern?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the attack pattern')}
      >
        <AttackPatternForm
          initialValues={initialValues}
          editing
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
        />
      </Drawer>
    </>
  );
};

export default AttackPatternPopover;
