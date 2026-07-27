import { Backdrop, Typography } from '@mui/material';
import { type FunctionComponent, useCallback, useState } from 'react';

import { addTenant } from '../../../../../actions/platform/tenants/tenant-action';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import useDialog from '../../../../../components/common/dialog/useDialog';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { type TenantInput, type TenantOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAuth from '../../../../../utils/hooks/useAuth';
import TenantForm from './TenantForm';

interface Props { onCreate: (result: TenantOutput) => void }

const TenantCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { reloadUserTenants } = useAuth();
  const [loading, setLoading] = useState(false);

  const { open, handleOpen, handleClose } = useDialog();

  const handleSubmit = useCallback(
    async (data: TenantInput) => {
      setLoading(true);
      // Close the drawer immediately so the user isn't blocked
      handleClose();

      try {
        const result = await dispatch(addTenant(data));

        if (!result?.result) {
          return result;
        }

        const createdTenant = result.entities.tenants[result.result];
        onCreate(createdTenant);
        await reloadUserTenants();

        return result;
      } finally {
        setLoading(false);
      }
    },
    [dispatch, onCreate, reloadUserTenants, handleClose],
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
      <Backdrop
        open={loading}
        sx={{
          zIndex: theme => theme.zIndex.drawer + 1,
          flexDirection: 'column',
          gap: 2,
        }}
      >
        <Loader />
        <Typography variant="h6" color="inherit">
          {t('Creating tenant…')}
        </Typography>
      </Backdrop>
    </>
  );
};

export default TenantCreate;
