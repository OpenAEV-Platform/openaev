import { type FunctionComponent, useCallback, useMemo } from 'react';

import { DEFAULT_TENANT_UUID, updateTenantUser } from '../../../../actions/tenant/users/user-tenant-action';
import { TENANT_USER_SCHEMA_KEY } from '../../../../actions/tenant/users/user-tenant-schema';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { UserInput, UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import UserForm from './UserForm';

interface Props {
  user: UserOutput;
  open: boolean;
  onClose: () => void;
  onUpdate?: (result: UserOutput) => void;
}

const UserUpdate: FunctionComponent<Props> = ({
  user,
  open,
  onClose,
  onUpdate,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const initialValues = useMemo<UserInput>(
    () => ({
      user_email: user.user_email,
      user_firstname: user.user_firstname ?? '',
      user_lastname: user.user_lastname ?? '',
      user_phone: user.user_phone ?? '',
      user_phone2: user.user_phone2 ?? '',
      user_organization: user.user_organization_id ?? '',
      user_tags: user.user_tags ?? [],
    }),
    [user],
  );

  const handleSubmit = useCallback(
    async (data: UserInput) => {
      const result = await dispatch(updateTenantUser(DEFAULT_TENANT_UUID, user.user_id, data));
      if (!result?.result) {
        return;
      }
      const updatedUser = result.entities[TENANT_USER_SCHEMA_KEY][result.result];
      onUpdate?.(updatedUser);
      onClose();
    },
    [dispatch, user.user_id, onUpdate, onClose],
  );

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Update user')}
    >
      <UserForm
        initialValues={initialValues}
        editing
        hasPassword={user.user_has_password}
        hasPgpKey={user.user_has_pgp_key}
        onSubmit={handleSubmit}
        onCancel={onClose}
      />
    </Drawer>
  );
};

export default UserUpdate;

