import { type FunctionComponent, useCallback, useContext, useMemo, useState } from 'react';

import { deleteTenant } from '../../../../actions/tenants/tenant-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../components/i18n';
import type { TenantOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import TenantUpdate from './tenant/TenantUpdate';

type ActionType = 'Update' | 'Delete';

interface Props {
  tenant: TenantOutput;
  actions: ActionType[];
  onUpdate?: (result: TenantOutput) => void;
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const TenantPopover: FunctionComponent<Props> = ({
  tenant,
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
    await dispatch(deleteTenant(tenant.tenant_id));
    handleCloseDelete();
    onDelete?.(tenant.tenant_id);
  }, [dispatch, tenant.tenant_id, onDelete, handleCloseDelete]);

  // Button Popover
  const entries = useMemo(() => {
    const result = [];

    if (
      actions.includes('Update')
    ) {
      result.push({
        label: t('Update'),
        action: handleOpenEdit,
        userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TENANTS),
      });
    }

    if (
      actions.includes('Delete')
    ) {
      result.push({
        label: t('Delete'),
        action: handleOpenDelete,
        userRight: ability.can(ACTIONS.DELETE, SUBJECTS.TENANTS),
      });
    }

    return result;
  }, [actions, ability, handleOpenEdit, handleOpenDelete]);

  return (
    <>
      {entries.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
      {actions.includes(('Update'))
        && (
          <TenantUpdate
            tenant={tenant}
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
            text={`${t('Do you want to delete this tenant?')}`}
          />
        )}
    </>
  );
};

export default TenantPopover;
