import { type FunctionComponent, useCallback, useMemo } from 'react';

import { updateTenant } from '../../../../../actions/tenants/tenant-actions';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type TenantInput, type TenantOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
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
      onClose();
    },
    [dispatch, tenant.tenant_id, onUpdate, onClose],
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
