import { type FunctionComponent, useMemo } from 'react';

import { type UserType } from '../../../../../actions/users/users-helper';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type UserInput, type UserOutput } from '../../../../../utils/api-types';
import UserForm from './UserForm';

interface UserUpdateProps {
  user: UserOutput;
  open: boolean;
  onClose: () => void;
  onSubmit: (data: UserInput) => void;
  type?: UserType;
}

const UserUpdate: FunctionComponent<UserUpdateProps> = ({
  user,
  open,
  onClose,
  onSubmit,
  type = 'TENANT',
}) => {
  const { t } = useFormatter();

  const updateTitle = type === 'PLATFORM' ? t('Update platform user') : t('Update the user');

  const initialValues = useMemo<UserInput>(() => ({
    user_email: user.user_email ?? '',
    user_firstname: user.user_firstname ?? '',
    user_lastname: user.user_lastname ?? '',
    user_pgp_key: user.user_pgp_key ?? '',
    user_phone: user.user_phone ?? '',
    user_phone2: user.user_phone2 ?? '',
    user_organization: user.user_organization_id ?? '',
    user_tags: user.user_tags ? [...user.user_tags] : [],
    user_admin: user.user_admin ?? false,
    user_tenants: user.user_tenants?.map(t => t.tenant_id).filter(Boolean) as string[] ?? [],
  }), [user]);

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={updateTitle}
    >
      <UserForm
        initialValues={initialValues}
        editing
        onSubmit={onSubmit}
        handleClose={onClose}
        type={type}
      />
    </Drawer>
  );
};

export default UserUpdate;
