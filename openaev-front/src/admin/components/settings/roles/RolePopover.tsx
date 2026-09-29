import { type FunctionComponent, useCallback, useContext, useMemo } from 'react';

import ButtonPopover from '../../../../components/common/ButtonPopover';
import useDialog from '../../../../components/common/dialog/useDialog';
import DialogDelete from '../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../components/i18n';
import type { RoleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS } from '../../../../utils/permissions/types';
import { useRoleScope } from './RoleScopeContext';
import RoleUpdate from './RoleUpdate';

type ActionType = 'Update' | 'Delete';

interface Props {
  role: RoleOutput;
  /** Both actions by default; each one still hides itself when the capability is missing. */
  actions?: ActionType[];
  onUpdate?: (result: RoleOutput) => void;
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const RolePopover: FunctionComponent<Props> = ({
  role,
  actions = ['Update', 'Delete'],
  onUpdate,
  onDelete,
  inList = false,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { remove, subject } = useRoleScope();

  const editDialog = useDialog();
  const deleteDialog = useDialog();

  const handleDelete = useCallback(async () => {
    await dispatch(remove(role.role_id));
    deleteDialog.handleClose();
    onDelete?.(role.role_id);
  }, [dispatch, remove, role.role_id, onDelete, deleteDialog.handleClose]);

  const entries = useMemo(() => {
    const result = [];
    if (actions.includes('Update')) {
      result.push({
        label: t('Update'),
        action: editDialog.handleOpen,
        userRight: ability.can(ACTIONS.MANAGE, subject),
      });
    }
    if (actions.includes('Delete')) {
      result.push({
        label: t('Delete'),
        action: deleteDialog.handleOpen,
        userRight: ability.can(ACTIONS.DELETE, subject),
      });
    }
    return result;
  }, [actions, t, ability, subject, editDialog.handleOpen, deleteDialog.handleOpen]);

  return (
    <>
      {entries.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
      {actions.includes('Update')
        && (
          <RoleUpdate
            role={role}
            open={editDialog.open}
            onClose={editDialog.handleClose}
            onUpdate={onUpdate}
          />
        )}
      {actions.includes('Delete')
        && (
          <DialogDelete
            {...deleteDialog.dialogProps}
            handleSubmit={handleDelete}
            text={`${t('Do you want to delete the role:')} ${role.role_name}?`}
          />
        )}
    </>
  );
};

export default RolePopover;
