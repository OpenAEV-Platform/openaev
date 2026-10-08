import { type FunctionComponent, useCallback, useMemo, useState } from 'react';

import { type UserType } from '../../../../../actions/users/users-helper';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import { type UserInput, type UserOutput } from '../../../../../utils/api-types';
import { useAbility } from '../../../../../utils/permissions/permissionsContext';
import { type Actions, PERMISSION_REQUIRED, type Subjects } from '../../../../../utils/permissions/types';
import UserUpdate from './UserUpdate';

type ActionType = 'Update' | 'Delete';

interface UserPopoverProps {
  user: UserOutput;
  actions?: ActionType[];
  onSubmitUpdate: (data: UserInput) => void;
  onSubmitDelete: () => void;
  deleteMessage?: string;
  type?: UserType;
  permissions: {
    manage: [Actions, Subjects];
    delete: [Actions, Subjects];
  };
  inList?: boolean;
}

const UserPopover: FunctionComponent<UserPopoverProps> = ({
  user,
  actions = [],
  onSubmitUpdate,
  onSubmitDelete,
  deleteMessage,
  type = 'TENANT',
  permissions,
  inList = false,
}) => {
  const { t } = useFormatter();
  const ability = useAbility();

  // Edition
  const [isEditOpen, setIsEditOpen] = useState(false);
  const handleOpenEdit = useCallback(() => setIsEditOpen(true), []);
  const handleCloseEdit = useCallback(() => setIsEditOpen(false), []);

  const handleUpdate = useCallback((data: UserInput) => {
    onSubmitUpdate(data);
    handleCloseEdit();
  }, [onSubmitUpdate, handleCloseEdit]);

  // Deletion
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const handleOpenDelete = useCallback(() => setIsDeleteOpen(true), []);
  const handleCloseDelete = useCallback(() => setIsDeleteOpen(false), []);

  const handleDelete = useCallback(() => {
    onSubmitDelete();
    handleCloseDelete();
  }, [onSubmitDelete, handleCloseDelete]);

  // Entries
  const entries = useMemo(() => {
    const canManage = ability.can(permissions.manage[0], permissions.manage[1]);
    const canDelete = ability.can(permissions.delete[0], permissions.delete[1]);
    const result: {
      label: string;
      action: () => void;
      userRight: boolean;
      disabled: boolean;
      disabledMessage: string;
    }[] = [];
    if (actions.includes('Update')) {
      result.push({
        label: t('Update'),
        action: handleOpenEdit,
        userRight: true,
        disabled: !canManage,
        disabledMessage: PERMISSION_REQUIRED,
      });
    }
    if (actions.includes('Delete')) {
      result.push({
        label: t('Delete'),
        action: handleOpenDelete,
        userRight: true,
        disabled: !canDelete,
        disabledMessage: PERMISSION_REQUIRED,
      });
    }
    return result;
  }, [actions, ability, permissions, handleOpenEdit, handleOpenDelete, t]);

  return (
    <>
      {entries.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
      {actions.includes('Update') && (
        <UserUpdate
          user={user}
          open={isEditOpen}
          onClose={handleCloseEdit}
          onSubmit={handleUpdate}
          type={type}
        />
      )}
      {actions.includes('Delete') && (
        <DialogDelete
          open={isDeleteOpen}
          handleClose={handleCloseDelete}
          handleSubmit={handleDelete}
          text={deleteMessage ?? t('Do you want to delete this user?')}
        />
      )}
    </>
  );
};

export default UserPopover;
