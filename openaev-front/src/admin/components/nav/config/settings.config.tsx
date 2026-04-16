import { SettingsOutlined } from '@mui/icons-material';

import { type LeftMenuItem } from '../../../../components/common/menu/leftmenu/leftmenu-model';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

const settingsEntries = (ability: AppAbility): LeftMenuItem[] =>
  (
    [{
      path: `/admin/settings`,
      icon: () => (<SettingsOutlined />),
      label: 'Settings',
      href: 'settings',
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
      subItems: [
        {
          link: '/admin/settings/parameters',
          label: 'Parameters',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
        {
          link: '/admin/settings/security',
          label: 'Security',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
        {
          link: '/admin/settings/asset_rules',
          label: 'Customization',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
        {
          link: '/admin/settings/taxonomies',
          label: 'Taxonomies',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
        {
          link: '/admin/settings/data_ingestion',
          label: 'Data ingestion',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
        {
          link: '/admin/settings/experience',
          label: 'Filigran Experience',
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS),
        },
      ],
    }]);
export default settingsEntries;
