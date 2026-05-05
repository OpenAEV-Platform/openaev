import { SettingsOutlined } from '@mui/icons-material';

import { type LeftMenuItem } from '../../../../components/common/menu/leftmenu/leftmenu-model';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { ACTIONS, type Actions, SUBJECTS, type Subjects } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';

export const SETTINGS_LABEL = 'Settings';

/**
 * All capability checks that grant access to at least one settings sub-page.
 * Used by ProtectedRoute to guard the parent `/settings` route
 * and derived automatically by settingsEntries for the left menu.
 */
export const SETTINGS_ACCESS_CHECKS: {
  action: Actions;
  subject: Subjects;
}[] = [
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.TENANT_SETTINGS,
  },
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.PLATFORM_SETTINGS,
  },
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES,
  },
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.TENANTS,
  },
];

const settingsEntries = (ability: AppAbility): LeftMenuItem[] => {
  const isMultiTenancyEnabled = isFeatureEnabled('MULTI_TENANCY');
  const canAccessTenantSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const canAccessPlatformSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS);
  const canAccessPlatformUGR = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES) && isMultiTenancyEnabled;
  const canAccessTenants = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS) && isMultiTenancyEnabled;

  const subItems = [
    {
      link: '/admin/settings/parameters',
      label: 'Parameters',
      userRight: canAccessTenantSettings,
    },
    {
      link: '/admin/settings/security',
      label: 'Security',
      userRight: canAccessTenantSettings || canAccessPlatformUGR || canAccessTenants,
    },
    {
      link: '/admin/settings/asset_rules',
      label: 'Customization',
      userRight: canAccessTenantSettings,
    },
    {
      link: '/admin/settings/taxonomies',
      label: 'Taxonomies',
      userRight: canAccessTenantSettings,
    },
    {
      link: '/admin/settings/data_ingestion',
      label: 'Data ingestion',
      userRight: canAccessTenantSettings,
    },
    {
      link: '/admin/settings/experience',
      label: 'Filigran Experience',
      userRight: canAccessTenantSettings || canAccessPlatformSettings,
    },
  ];

  return [{
    path: '/admin/settings',
    icon: () => (<SettingsOutlined />),
    label: SETTINGS_LABEL,
    href: 'settings',
    userRight: subItems.some(item => item.userRight),
    subItems,
  }];
};
export default settingsEntries;
