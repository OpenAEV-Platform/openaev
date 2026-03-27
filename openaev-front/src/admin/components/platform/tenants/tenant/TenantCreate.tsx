import { type FunctionComponent, useCallback, useState } from 'react';

import { addTenant } from '../../../../../actions/tenants/tenant-actions';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type TenantInput, type TenantOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAuth from '../../../../../utils/hooks/useAuth';
import TenantForm from './TenantForm';

interface Props { onCreate: (result: TenantOutput) => void }

const TenantCreate: FunctionComponent<Props> = ({ onCreate }) => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { reloadUserTenants } = useAuth();

  const handleOpen = useCallback(() => {
    setOpen(true);
  }, []);

  const handleClose = useCallback(() => {
    setOpen(false);
  }, []);

  const handleSubmit = useCallback(
    async (data: TenantInput) => {
      const result = await dispatch(addTenant(data));

      if (!result?.result) {
        return result;
      }

      const createdTenant = result.entities.tenants[result.result];
      onCreate(createdTenant);
      await reloadUserTenants(createdTenant.tenant_id);
      setOpen(false);

      return result;
    },
    [dispatch, onCreate],
  );

  return (
    <>
      <ButtonCreate onClick={handleOpen} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new tenant')}
      >
        <TenantForm
          onSubmit={handleSubmit}
          onCancel={handleClose}
        />
      </Drawer>
    </>
  );
};

export default TenantCreate;
