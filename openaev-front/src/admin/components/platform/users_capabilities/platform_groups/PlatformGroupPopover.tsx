import { type FunctionComponent, useCallback, useContext, useMemo, useState } from 'react';

import {
  deletePlatformGroup,
  updatePlatformGroupRoles,
  updatePlatformGroupUsers,
} from '../../../../../actions/platform/platform-group/platform-group-action';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import type { PlatformGroupOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import PlatformGroupManageRoles from './PlatformGroupManageRoles';
import PlatformGroupManageUsers from './PlatformGroupManageUsers';
import PlatformGroupUpdate from './PlatformGroupUpdate';

type ActionType = 'Update' | 'Delete' | 'Manage users' | 'Manage roles';

interface Props {
  platformGroup: PlatformGroupOutput;
  actions: ActionType[];
  onUpdate?: (result: PlatformGroupOutput) => void;
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const PlatformGroupPopover: FunctionComponent<Props> = ({
  platformGroup,
  actions = [],
  onUpdate,
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [isEditOpen, setIsEditOpen] = useState(false);
  const handleOpenEdit = useCallback(() => {
    setIsEditOpen(true);
  }, []);
  const handleCloseEdit = useCallback(() => {
    setIsEditOpen(false);
  }, []);

  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const handleOpenDelete = useCallback(() => {
    setIsDeleteOpen(true);
  }, []);
  const handleCloseDelete = useCallback(() => {
    setIsDeleteOpen(false);
  }, []);
  const handleDelete = useCallback(async () => {
    await dispatch(deletePlatformGroup(platformGroup.platform_group_id));
    handleCloseDelete();
    onDelete?.(platformGroup.platform_group_id);
  }, [dispatch, platformGroup.platform_group_id, onDelete, handleCloseDelete]);

  const [isUsersOpen, setIsUsersOpen] = useState(false);
  const handleOpenUsers = useCallback(() => {
    setIsUsersOpen(true);
  }, []);
  const handleCloseUsers = useCallback(() => {
    setIsUsersOpen(false);
  }, []);

  const [isRolesOpen, setIsRolesOpen] = useState(false);
  const handleOpenRoles = useCallback(() => {
    setIsRolesOpen(true);
  }, []);
  const handleCloseRoles = useCallback(() => {
    setIsRolesOpen(false);
  }, []);

  const submitUpdateUsers = useCallback(async (userIds: string[]) => {
    await updatePlatformGroupUsers(platformGroup.platform_group_id, { platform_group_users: userIds });
  }, [platformGroup.platform_group_id]);

  const submitUpdateRoles = useCallback(async (roleIds: string[]) => {
    await updatePlatformGroupRoles(platformGroup.platform_group_id, { platform_group_platform_roles: roleIds });
  }, [platformGroup.platform_group_id]);

  const entries = useMemo(() => {
    const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_GROUPS_AND_ROLES);
    const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.PLATFORM_GROUPS_AND_ROLES);
    const result = [];
    if (actions.includes('Update')) {
      result.push({
        label: t('Update'),
        action: handleOpenEdit,
        userRight: canManage,
      });
    }
    if (actions.includes('Manage users')) {
      result.push({
        label: t('Manage users'),
        action: handleOpenUsers,
        userRight: canManage,
      });
    }
    if (actions.includes('Manage roles')) {
      result.push({
        label: t('Manage roles'),
        action: handleOpenRoles,
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
  }, [actions, ability, handleOpenEdit, handleOpenDelete, handleOpenUsers, handleOpenRoles]);

  return (
    <>
      {entries.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
      {actions.includes('Update')
        && (
          <PlatformGroupUpdate
            platformGroup={platformGroup}
            open={isEditOpen}
            onClose={handleCloseEdit}
            onUpdate={onUpdate}
          />
        )}
      {actions.includes('Manage users')
        && (
          <PlatformGroupManageUsers
            platformGroupId={platformGroup.platform_group_id}
            open={isUsersOpen}
            onClose={handleCloseUsers}
            onSubmit={submitUpdateUsers}
          />
        )}
      {actions.includes('Manage roles')
        && (
          <PlatformGroupManageRoles
            platformGroupId={platformGroup.platform_group_id}
            open={isRolesOpen}
            onClose={handleCloseRoles}
            onSubmit={submitUpdateRoles}
          />
        )}
      {actions.includes('Delete')
        && (
          <DialogDelete
            open={isDeleteOpen}
            handleClose={handleCloseDelete}
            handleSubmit={handleDelete}
            text={`${t('Do you want to delete this platform group?')}`}
          />
        )}
    </>
  );
};

export default PlatformGroupPopover;
