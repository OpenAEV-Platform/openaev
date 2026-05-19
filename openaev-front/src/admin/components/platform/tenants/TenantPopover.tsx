import { type FunctionComponent, useCallback, useContext, useMemo, useState } from 'react';

import { reactivateTenant, softDeleteTenant } from '../../../../actions/platform/tenants/tenant-action';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogConfirmation from '../../../../components/common/DialogConfirmation';
import DialogDelete from '../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../components/i18n';
import type { TenantOutput } from '../../../../utils/api-types';
import useAuth from '../../../../utils/hooks/useAuth';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import TenantUpdate from './tenant/TenantUpdate';

type ActionType = 'Update' | 'Delete' | 'Reactivate';

interface Props {
  tenant: TenantOutput;
  actions: ActionType[];
  onUpdate?: (result: TenantOutput) => void;
  onDelete?: (result: TenantOutput) => void;
  onReactivate?: (result: TenantOutput) => void;
  inList?: boolean;
}

const TenantPopover: FunctionComponent<Props> = ({
  tenant,
  actions = [],
  onUpdate,
  onDelete,
  onReactivate,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);
  const { reloadUserTenants } = useAuth();

  // Edition
  const [isEditOpen, setIsEditOpen] = useState(false);
  const handleOpenEdit = useCallback(() => {
    setIsEditOpen(true);
  }, []);
  const handleCloseEdit = useCallback(() => {
    setIsEditOpen(false);
  }, []);

  // Soft deletion
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const handleOpenDelete = useCallback(() => {
    setIsDeleteOpen(true);
  }, []);
  const handleCloseDelete = useCallback(() => {
    setIsDeleteOpen(false);
  }, []);
  const handleDelete = useCallback(async () => {
    const result = await softDeleteTenant(tenant.tenant_id);
    handleCloseDelete();
    onDelete?.(result.data as TenantOutput);
    await reloadUserTenants();
  }, [tenant.tenant_id, onDelete, handleCloseDelete, reloadUserTenants]);

  // Reactivation
  const [isReactivateOpen, setIsReactivateOpen] = useState(false);
  const handleOpenReactivate = useCallback(() => {
    setIsReactivateOpen(true);
  }, []);
  const handleCloseReactivate = useCallback(() => {
    setIsReactivateOpen(false);
  }, []);
  const handleReactivate = useCallback(async () => {
    const result = await reactivateTenant(tenant.tenant_id);
    handleCloseReactivate();
    onReactivate?.(result.data as TenantOutput);
    await reloadUserTenants();
  }, [tenant.tenant_id, onReactivate, handleCloseReactivate, reloadUserTenants]);

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

    if (
      actions.includes('Reactivate')
    ) {
      result.push({
        label: t('Reactivate'),
        action: handleOpenReactivate,
        userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TENANTS),
      });
    }

    return result;
  }, [actions, ability, handleOpenEdit, handleOpenDelete, handleOpenReactivate]);

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
            text={`${t('Do you want to soft-delete this tenant? Data will be preserved for 30 days, during which an admin can reactivate it.')}`}
          />
        )}
      {actions.includes('Reactivate')
        && (
          <DialogConfirmation
            open={isReactivateOpen}
            handleClose={handleCloseReactivate}
            handleSubmit={handleReactivate}
            text={`${t('Do you want to reactivate this tenant?')}`}
            submitLabel={t('Reactivate')}
          />
        )}
    </>
  );
};

export default TenantPopover;
