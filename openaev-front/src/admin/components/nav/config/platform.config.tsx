import { DeviceHubOutlined } from '@mui/icons-material';

import { type LeftMenuItem } from '../../../../components/common/menu/leftmenu/leftmenu-model';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';

const platformEntries = (ability: AppAbility): LeftMenuItem[] => {
  if (!isFeatureEnabled('MULTI_TENANCY')) {
    return [];
  }

  return [
    {
      path: '/admin/platform',
      icon: () => (<DeviceHubOutlined />),
      label: 'Platform',
      href: 'platform',
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS),
      subItems: [],
    },
  ];
};

export default platformEntries;
