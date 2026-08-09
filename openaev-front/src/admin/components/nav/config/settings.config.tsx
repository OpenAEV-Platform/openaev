import { SettingsOutlined } from '@mui/icons-material';

import { type NavMenuItem } from '../../../../components/common/menu/navbar/nav-menu-model';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { ACTIONS, type Actions, SUBJECTS, type Subjects } from '../../../../utils/permissions/types';

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
  {
    // Lessons learned templates live under Settings > Customization.
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.LESSONS_LEARNED,
  },
];

const settingsEntries = (ability: AppAbility): NavMenuItem[] => {
  const canAccessTenantSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const canAccessPlatformSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS);
  const canAccessPlatformUGR = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES);
  const canAccessTenants = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS);
  const canAccessLessonsLearned = ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED);

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
      // Section root: redirects to asset_rules; Notifiers and Lessons learned
      // live in the Customization right submenu (OpenCTI-aligned), not as
      // direct entries.
      link: '/admin/settings/customization',
      label: 'Customization',
      userRight: canAccessTenantSettings || canAccessLessonsLearned,
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
