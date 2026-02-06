import { DeviceHubOutlined } from '@mui/icons-material';

import { type LeftMenuItem } from '../../../../components/common/menu/leftmenu/leftmenu-model';
import { useFormatter } from '../../../../components/i18n';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';

export const PLATFORM_ROUTE = '/admin/platform';
export const PLATFORM_TENANTS_ROUTE = `${PLATFORM_ROUTE}/tenants`;

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
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
      subItems: [
        {
          link: PLATFORM_TENANTS_ROUTE,
          label: 'Tenants',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
        },
      ],
    },
  ];
};

export default platformEntries;
