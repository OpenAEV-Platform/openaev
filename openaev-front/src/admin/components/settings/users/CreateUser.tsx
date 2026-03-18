import { type FunctionComponent, useCallback, useState } from 'react';

import { addTenantUser, DEFAULT_TENANT_UUID } from '../../../../actions/tenant/users/user-tenant-action';
import { TENANT_USER_SCHEMA_KEY } from '../../../../actions/tenant/users/user-tenant-schema';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { UserInput, UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import UserForm from './UserForm';

interface Props {
  onCreate?: (user: UserOutput) => void;
}

const CreateUser: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  const handleOpen = useCallback(() => setOpen(true), []);
  const handleClose = useCallback(() => setOpen(false), []);

  const handleSubmit = useCallback(
    async (data: UserInput) => {
      const result = await dispatch(addTenantUser(DEFAULT_TENANT_UUID, data));
      if (!result?.result) {
        return;
      }
      const createdUser = result.entities[TENANT_USER_SCHEMA_KEY][result.result];
      onCreate?.(createdUser);
      handleClose();
    },
    [dispatch, onCreate, handleClose],
  );

  return (
    <>
      <ButtonCreate onClick={handleOpen} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Add a user')}
      >
        <UserForm
          onSubmit={handleSubmit}
          onCancel={handleClose}
        />
      </Drawer>
    </>
  );
};

export default CreateUser;
