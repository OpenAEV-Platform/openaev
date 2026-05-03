import { type FunctionComponent, useMemo } from 'react';

import { type OrganizationHelper } from '../../../../../actions/helper';
import { type TagHelper } from '../../../../../actions/tags/tag-helper';
import { type UserInputForm, type UserType } from '../../../../../actions/users/users-helper';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type UserOutput } from '../../../../../utils/api-types';
import { organizationOption, tagOptions, tenantOptions } from '../../../../../utils/Option';
import UserForm from './UserForm';

interface UserUpdateProps {
  user: UserOutput;
  open: boolean;
  onClose: () => void;
  onSubmit: (data: UserInputForm) => void;
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

  const { organizationsMap, tagsMap } = useHelper(
    (helper: OrganizationHelper & TagHelper) => ({
      organizationsMap: helper.getOrganizationsMap(),
      tagsMap: helper.getTagsMap(),
    }),
  );

  const initialValues = useMemo<UserInputForm>(() => ({
    user_email: user.user_email ?? '',
    user_firstname: user.user_firstname ?? '',
    user_lastname: user.user_lastname ?? '',
    user_pgp_key: user.user_pgp_key ?? '',
    user_phone: user.user_phone ?? '',
    user_phone2: user.user_phone2 ?? '',
    user_organization: organizationOption(user.user_organization_id, organizationsMap),
    user_tags: tagOptions(user.user_tags, tagsMap),
    user_admin: user.user_admin ?? false,
    user_tenants: tenantOptions(user.user_tenants),
  }), [user, organizationsMap, tagsMap]);

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
