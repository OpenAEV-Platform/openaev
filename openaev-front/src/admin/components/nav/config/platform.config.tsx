import { DeviceHubOutlined } from '@mui/icons-material';

import { type LeftMenuItem } from '../../../../components/common/menu/leftmenu/leftmenu-model';
import { useFormatter } from '../../../../components/i18n';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import { SETTINGS_PATH } from '../../platform/settings/routes/SettingsRoutes';
import { TENANTS_PATH } from '../../platform/tenants/routes/TenantsRoutes';
import { PLATFORM_USERS_CAPABILITIES_ROUTE } from '../../platform/users_capabilities/users-capabilities-constants';

export const PLATFORM_ROUTE = '/admin/platform';
export const PLATFORM_PARAMETERS_ROUTE = `${PLATFORM_ROUTE}/${SETTINGS_PATH}`;
export const PLATFORM_TENANTS_ROUTE = `${PLATFORM_ROUTE}/${TENANTS_PATH}`;

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
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS)
        || ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS)
        || ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
      subItems: [
        {
          link: PLATFORM_PARAMETERS_ROUTE,
          label: 'Parameters',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
        },
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
