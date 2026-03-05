import { type FunctionComponent, useCallback } from 'react';

import { addTenant } from '../../../../../actions/platform/tenants/tenant-action';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import Drawer from '../../../../../components/common/Drawer';
import useDialog from '../../../../../components/common/dialog/useDialog';
import { useFormatter } from '../../../../../components/i18n';
import { type TenantInput, type TenantOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import TenantForm from './TenantForm';

interface Props { onCreate: (result: TenantOutput) => void }

const TenantCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { open, handleOpen, handleClose } = useDialog();

  const handleSubmit = useCallback(
    async (data: TenantInput) => {
      const result = await dispatch(addTenant(data));

      if (!result?.result) {
        return result;
      }

      const createdTenant = result.entities.tenants[result.result];
      onCreate(createdTenant);
      handleClose();

      return result;
    },
    [dispatch, onCreate, handleClose],
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
