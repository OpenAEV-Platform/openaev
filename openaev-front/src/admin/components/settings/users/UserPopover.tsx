import { type FunctionComponent, useCallback, useContext, useMemo, useState } from 'react';

import { type UserInputForm } from '../../../../actions/users/users-helper';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { ChangePasswordInput, UserOutput } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { type Actions, type Subjects } from '../../../../utils/permissions/types';
import UserPasswordForm from './UserPasswordForm';
import UserUpdate from './UserUpdate';

type ActionType = 'Update' | 'Update password' | 'Delete';

interface UserPopoverProps {
  user: UserOutput;
  actions?: ActionType[];
  onSubmitUpdate: (data: UserInputForm) => void;
  onSubmitDelete: () => void;
  onSubmitPassword?: (data: ChangePasswordInput) => void;
  deleteMessage?: string;
  updateTitle?: string;
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
  onSubmitPassword,
  deleteMessage,
  updateTitle,
  permissions,
  inList = false,
}) => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  // Edition
  const [isEditOpen, setIsEditOpen] = useState(false);
  const handleOpenEdit = useCallback(() => setIsEditOpen(true), []);
  const handleCloseEdit = useCallback(() => setIsEditOpen(false), []);

  const handleUpdate = useCallback((data: UserInputForm) => {
    onSubmitUpdate(data);
    handleCloseEdit();
  }, [onSubmitUpdate, handleCloseEdit]);

  // Password
  const [isPasswordOpen, setIsPasswordOpen] = useState(false);
  const handleOpenPassword = useCallback(() => setIsPasswordOpen(true), []);
  const handleClosePassword = useCallback(() => setIsPasswordOpen(false), []);

  const handlePassword = useCallback((data: ChangePasswordInput) => {
    onSubmitPassword?.(data);
    handleClosePassword();
  }, [onSubmitPassword, handleClosePassword]);

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
    }[] = [];
    if (actions.includes('Update')) {
      result.push({
        label: t('Update'),
        action: handleOpenEdit,
        userRight: canManage,
      });
    }
    if (actions.includes('Update password') && onSubmitPassword) {
      result.push({
        label: t('Update password'),
        action: handleOpenPassword,
        userRight: canManage,
      });
    }
    if (actions.includes('Delete')) {
      result.push({
        label: t('Delete'),
        action: handleOpenDelete,
        userRight: canDelete,
      });
    }
    return result;
  }, [actions, ability, permissions, onSubmitPassword, handleOpenEdit, handleOpenPassword, handleOpenDelete, t]);

  return (
    <>
      {entries.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
      {actions.includes('Update') && (
        <UserUpdate
          user={user}
          open={isEditOpen}
          onClose={handleCloseEdit}
          onSubmit={handleUpdate}
          title={updateTitle}
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
      {actions.includes('Update password') && onSubmitPassword && (
        <Drawer
          open={isPasswordOpen}
          handleClose={handleClosePassword}
          title={t('Update the user password')}
        >
          <UserPasswordForm
            onSubmit={handlePassword}
            handleClose={handleClosePassword}
          />
        </Drawer>
      )}
    </>
  );
};

export default UserPopover;
