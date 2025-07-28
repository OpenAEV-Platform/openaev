import { type FunctionComponent, useState } from 'react';

import { deleteRole, updateRole } from '../../../../actions/roles/roles-actions';
import { type RoleInput, type RoleResult } from '../../../../actions/roles/roles-helper';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type RoleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import RoleForm from './RoleForm';

export interface RolePopoverProps {
  onDelete?: (result: string) => void;
  onUpdate?: (result: RoleOutput) => void;
  role: RoleOutput;
}

const RolePopover: FunctionComponent<RolePopoverProps> = ({ onDelete, onUpdate, role }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  // Deletion
  const [deletion, setDeletion] = useState(false);

  const handleDelete = () => {
    setDeletion(true);
  };
  const submitDelete = () => {
    dispatch(deleteRole(role.role_id)).then(
      () => {
        if (onDelete) {
          onDelete(role.role_id);
        }
      },
    );
    setDeletion(false);
  };

  // Edition
  const [openUpdate, setOpenUpdate] = useState(false);
  const handleUpdate = () => {
    setOpenUpdate(true);
  };

  // Button Popover
  const entries = [];
  if (onUpdate) entries.push({
    label: t('Update'),
    action: () => handleUpdate(),
  });
  if (onDelete) entries.push({
    label: t('Delete'),
    action: () => handleDelete(),
  });

  const onSubmit = (data: RoleInput) => {
    dispatch(updateRole(role.role_id, data))
      .then((result: RoleResult) => {
        if (onUpdate) {
          const roleUpdated = result.entities.roles[result.result];
          onUpdate(roleUpdated);
        }
        return (result.result ? setOpenUpdate(false) : result);
      });
  };

  return entries.length > 0 && (
    <>
      <ButtonPopover entries={entries} variant="icon" />

      <DialogDelete
        open={deletion}
        handleClose={() => setDeletion(false)}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete the role:')} ${role.role_name}?`}
      />

      <Drawer
        open={openUpdate}
        handleClose={() => setOpenUpdate(false)}
        title={t('Update role')}
      >
        <RoleForm onSubmit={onSubmit} handleClose={() => setOpenUpdate(false)} editing={true} initialValues={role} />
      </Drawer>
    </>
  );
};

export default RolePopover;
