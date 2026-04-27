import { type FunctionComponent, useMemo } from 'react';

import { type OrganizationHelper, TenantHelper } from '../../../../actions/helper';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { type UserInputForm, type UserType } from '../../../../actions/users/users-helper';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import type { UserOutput } from '../../../../utils/api-types';
import { organizationOption, tagOptions, tenantOptions } from '../../../../utils/Option';
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

  const { organizationsMap, tagsMap, tenantsMap } = useHelper(
    (helper: OrganizationHelper & TagHelper & TenantHelper) => ({
      organizationsMap: helper.getOrganizationsMap(),
      tagsMap: helper.getTagsMap(),
      tenantsMap: helper.getTenantsMap(),
    }),
  );


  const initialValues = useMemo<UserInputForm>(() => ({
    ...user,
    user_organization: organizationOption(user.user_organization_id, organizationsMap),
    user_tags: tagOptions(user.user_tags, tagsMap),
    user_tenants: tenantOptions(user.user_tenants, tenantsMap),
  }), [user, organizationsMap, tagsMap, tenantsMap]);


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
