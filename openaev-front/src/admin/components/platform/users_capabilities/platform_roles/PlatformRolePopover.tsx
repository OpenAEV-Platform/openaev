import { type FunctionComponent, useCallback, useContext, useMemo, useState } from 'react';

import { deletePlatformRole } from '../../../../../actions/platform/platform-role/platform-role-action';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import type { PlatformRoleOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import PlatformRoleUpdate from './PlatformRoleUpdate';

type ActionType = 'Update' | 'Delete';

interface Props {
  platformRole: PlatformRoleOutput;
  actions: ActionType[];
  onUpdate?: (result: PlatformRoleOutput) => void;
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const PlatformRolePopover: FunctionComponent<Props> = ({
  platformRole,
  actions = [],
  onUpdate,
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  // Edition
  const [isEditOpen, setIsEditOpen] = useState(false);
  const handleOpenEdit = useCallback(() => {
    setIsEditOpen(true);
  }, []);
  const handleCloseEdit = useCallback(() => {
    setIsEditOpen(false);
  }, []);

  // Deletion
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const handleOpenDelete = useCallback(() => {
    setIsDeleteOpen(true);
  }, []);
  const handleCloseDelete = useCallback(() => {
    setIsDeleteOpen(false);
  }, []);
  const handleDelete = useCallback(async () => {
    await dispatch(deletePlatformRole(platformRole.platform_role_id));
    handleCloseDelete();
    onDelete?.(platformRole.platform_role_id);
  }, [dispatch, platformRole.platform_role_id, onDelete, handleCloseDelete]);

  // Button Popover
  const entries = useMemo(() => {
    const result = [];
    if (actions.includes('Update')) {
      result.push({
        label: t('Update'),
        action: handleOpenEdit,
        userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
      });
    }
    if (actions.includes('Delete')) {
      result.push({
        label: t('Delete'),
        action: handleOpenDelete,
        userRight: ability.can(ACTIONS.DELETE, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
      });
    }
    return result;
  }, [actions, ability, handleOpenEdit, handleOpenDelete]);

  return (
    <>
      {entries.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
      {actions.includes('Update')
        && (
          <PlatformRoleUpdate
            platformRole={platformRole}
            open={isEditOpen}
            onClose={handleCloseEdit}
            onUpdate={onUpdate}
          />
        )}
      {actions.includes('Delete')
        && (
          <DialogDelete
            open={isDeleteOpen}
            handleClose={handleCloseDelete}
            handleSubmit={handleDelete}
            text={`${t('Do you want to delete this platform role?')}`}
          />
        )}
    </>
  );
};

export default PlatformRolePopover;
