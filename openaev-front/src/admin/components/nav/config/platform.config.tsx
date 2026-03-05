import { DeviceHubOutlined } from '@mui/icons-material';

import { type LeftMenuItem } from '../../../../components/common/menu/leftmenu/leftmenu-model';
import { useFormatter } from '../../../../components/i18n';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import { USERS_CAPABILITIES_PATH } from '../../platform/users_capabilities/routes/UsersCapabilitiesRoutes';
import {TENANTS_PATH} from "../../platform/tenants/routes/TenantsRoutes";

export const PLATFORM_ROUTE = '/admin/platform';
export const PLATFORM_TENANTS_ROUTE = `${PLATFORM_ROUTE}/${TENANTS_PATH}`;
export const PLATFORM_USERS_CAPABILITIES_ROUTE = `${PLATFORM_ROUTE}/${USERS_CAPABILITIES_PATH}`;

const platformEntries = (ability: AppAbility): LeftMenuItem[] => {
  // Standard hooks
  const { t } = useFormatter();

  if (!isFeatureEnabled('MULTI_TENANCY')) {
    return [];
  }

  return [
    {
      path: PLATFORM_ROUTE,
      icon: () => (<DeviceHubOutlined />),
      label: t('Platform'),
      href: 'platform',
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS)
        || ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
      subItems: [
        {
          link: PLATFORM_TENANTS_ROUTE,
          label: 'Tenants',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS),
        },
        {
          link: PLATFORM_USERS_CAPABILITIES_ROUTE,
          label: 'Users & capabilities',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
        },
      ],
    },
  ];
};

export default platformEntries;
