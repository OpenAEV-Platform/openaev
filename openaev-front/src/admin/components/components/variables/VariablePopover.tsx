import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';

import ButtonPopover from '../../../../components/common/ButtonPopover';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type Variable, type VariableInput } from '../../../../utils/api-types';
import { PermissionsContext } from '../../common/Context';
import VariableForm from './VariableForm';

interface Props {
  variable: Variable;
  onEdit: (variable: Variable, data: VariableInput) => void;
  onDelete: (variable: Variable) => void;
}

const VariablePopover: FunctionComponent<Props> = ({
  variable,
  onEdit,
  onDelete,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const initialValues = (({
    variable_key,
    variable_description,
    variable_value,
  }) => ({
    variable_key,
    variable_description,
    variable_value,
  }))(variable);

  // Edition
  const [editVar, setEditVar] = useState(false);
  const submitEdit = (data: VariableInput) => {
    onEdit(variable, data);
    setEditVar(false);
  };
  const handleUpdate = () => {
    setEditVar(true);
  };

  // Deletion
  const [deleteVar, setDeleteVar] = useState(false);
  const submitDelete = () => {
    onDelete(variable);
    setDeleteVar(false);
  };
  const handleDelete = () => {
    setDeleteVar(true);
  };

  // Button Popover
  const entries = [{
    label: t('Update'),
    action: () => handleUpdate(),
    userRight: permissions.canManage,
  }, {
    label: t('Delete'),
    action: () => handleDelete(),
    userRight: permissions.canManage,
  }];

  return (
    <>
      <ButtonPopover
        entries={entries}
        variant="icon"
      />
      <Dialog
        open={deleteVar}
        TransitionComponent={Transition}
        onClose={() => setDeleteVar(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete the variable?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteVar(false)}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        TransitionComponent={Transition}
        open={editVar}
        onClose={() => setEditVar(false)}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Update the variable')}</DialogTitle>
        <DialogContent>
          <VariableForm
            initialValues={initialValues}
            editing
            onSubmit={submitEdit}
            handleClose={() => setEditVar(false)}
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default VariablePopover;
