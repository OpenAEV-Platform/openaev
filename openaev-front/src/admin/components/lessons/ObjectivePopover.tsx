import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import { type FunctionComponent, type MouseEvent, useContext, useState } from 'react';
import { type SubmitHandler } from 'react-hook-form';

import Drawer from '../../../components/common/Drawer';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { type Objective } from '../../../utils/api-types';
import { LessonContext } from '../common/Context';
import ObjectiveForm, { type ObjectiveFormInputs } from './ObjectiveForm';

interface Props {
  objective: Objective;
  isReadOnly?: boolean;
}

const ObjectivePopover: FunctionComponent<Props> = ({ objective, isReadOnly }) => {
  // Standard hooks
  const { t } = useFormatter();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [openDelete, setOpenDelete] = useState<boolean>(false);
  const [openEdit, setOpenEdit] = useState<boolean>(false);

  // Context
  const { onUpdateObjective, onDeleteObjective } = useContext(LessonContext);

  const handlePopoverOpen = (event: MouseEvent<HTMLElement>) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };
  const handlePopoverClose = () => setAnchorEl(null);

  const handleOpenEdit = () => {
    setOpenEdit(true);
    handlePopoverClose();
  };
  const handleCloseEdit = () => setOpenEdit(false);

  const onSubmitEdit: SubmitHandler<ObjectiveFormInputs> = data => onUpdateObjective(
    objective.objective_id,
    data,
  )
    .then(() => handleCloseEdit());

  const handleOpenDelete = () => {
    setOpenDelete(true);
    handlePopoverClose();
  };
  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    onDeleteObjective(objective.objective_id);
    handleCloseDelete();
  };

  const initialValues: Partial<ObjectiveFormInputs> = {
    objective_title: objective.objective_title ?? '',
    objective_description: objective.objective_description ?? '',
    objective_priority: objective.objective_priority,
  };

  return (
    <div>
      <IconButton
        onClick={handlePopoverOpen}
        aria-haspopup="true"
        size="small"
        color="primary"
        sx={{ borderRadius: 1 }}
        disabled={isReadOnly}
      >
        <MoreVert fontSize="small" />
      </IconButton>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handlePopoverClose}
      >
        <MenuItem onClick={handleOpenEdit}>
          {t('Update')}
        </MenuItem>
        <MenuItem onClick={handleOpenDelete}>
          {t('Delete')}
        </MenuItem>
      </Menu>
      <Dialog
        open={openDelete}
        TransitionComponent={Transition}
        onClose={handleCloseDelete}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this objective?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleCloseDelete}>
            {t('Cancel')}
          </Button>
          <Button variant="contained" color="primary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the objective')}
      >
        <ObjectiveForm
          initialValues={initialValues}
          editing
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
        />
      </Drawer>
    </div>
  );
};

export default ObjectivePopover;
