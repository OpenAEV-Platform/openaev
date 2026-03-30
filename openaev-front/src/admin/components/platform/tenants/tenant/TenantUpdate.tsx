import { type FunctionComponent, useCallback, useMemo } from 'react';

import { updateTenant } from '../../../../../actions/platform/tenants/tenant-action';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type TenantInput, type TenantOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAuth from '../../../../../utils/hooks/useAuth';
import TenantForm from './TenantForm';

interface Props {
  tenant: TenantOutput;
  open: boolean;
  onClose: () => void;
  onUpdate?: (result: TenantOutput) => void;
}

const TenantUpdate: FunctionComponent<Props> = ({
  tenant,
  open,
  onClose,
  onUpdate,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { reloadUserTenants } = useAuth();

  // Form

  const initialValues = useMemo<TenantInput>(
    () => ({
      tenant_name: tenant.tenant_name,
      tenant_description: tenant.tenant_description ?? '',
    }),
    [tenant],
  );

  const handleSubmit = useCallback(
    async (data: TenantInput) => {
      const result = await dispatch(updateTenant(tenant.tenant_id, data));

      if (!result?.result) {
        return;
      }

      const updatedTenant = result.entities.tenants[result.result];
      onUpdate?.(updatedTenant);
      await reloadUserTenants();
      onClose();
    },
    [dispatch, tenant.tenant_id, onUpdate, onClose, reloadUserTenants],
  );

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Update Tenant')}
    >
      <TenantForm
        initialValues={initialValues}
        editing
        onSubmit={handleSubmit}
        onCancel={onClose}
      />
    </Drawer>
  );
};

export default TenantUpdate;
