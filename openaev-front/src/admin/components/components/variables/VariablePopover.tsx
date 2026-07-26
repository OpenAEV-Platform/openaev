import { type FunctionComponent, useContext, useState } from 'react';

import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
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
    label: 'Update',
    action: () => handleUpdate(),
    userRight: permissions.canManage,
  }, {
    label: 'Delete',
    action: () => handleDelete(),
    userRight: permissions.canManage,
  }];

  return (
    <>
      <ButtonPopover
        entries={entries}
        variant="icon"
      />
      <DialogDelete
        open={deleteVar}
        handleClose={() => setDeleteVar(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete the variable?')}
      />
      <Drawer
        open={editVar}
        handleClose={() => setEditVar(false)}
        title={t('Update the variable')}
      >
        <VariableForm
          initialValues={initialValues}
          editing
          onSubmit={submitEdit}
          handleClose={() => setEditVar(false)}
        />
      </Drawer>
    </>
  );
};

export default VariablePopover;
