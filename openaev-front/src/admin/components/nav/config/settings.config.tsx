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
    subject: SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES,
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
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.MARKING_DEFINITION,
  },
  {
    // Lessons learned templates live under Settings > Customization.
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.LESSONS_LEARNED,
  },
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.TAGS,
  },
  {
    action: ACTIONS.MANAGE,
    subject: SUBJECTS.SESSIONS,
  },
  {
    action: ACTIONS.MANAGE,
    subject: SUBJECTS.PLATFORM_SESSIONS,
  },
];

export const canAccessTags = (ability: AppAbility): boolean => {
  return ability.can(ACTIONS.ACCESS, SUBJECTS.TAGS);
};

export const canAccessTenantSettings = (ability: AppAbility): boolean => {
  return ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
};

const settingsEntries = (ability: AppAbility): LeftMenuItem[] => {
  const hasTenantSettingsAccess = canAccessTenantSettings(ability);
  const canAccessTenantUsers = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES);
  const canAccessPlatformSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS);
  const canAccessPlatformUGR = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES);
  const canAccessTenants = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS);
  const canAccessMarkingDefinitions = isFeatureEnabled('MARKING')
    && ability.can(ACTIONS.ACCESS, SUBJECTS.MARKING_DEFINITION);
  const canAccessLessonsLearned = ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED);
  const hasTagsAccess = canAccessTags(ability);
  const canManageAnySessions = ability.can(ACTIONS.MANAGE, SUBJECTS.SESSIONS)
    || ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SESSIONS);

  const subItems = [
    {
      link: '/admin/settings/parameters',
      label: 'Parameters',
      userRight: hasTenantSettingsAccess,
    },
    {
      link: '/admin/settings/security',
      label: 'Security',
      userRight: hasTenantSettingsAccess || canAccessTenantUsers || canAccessPlatformUGR || canAccessTenants
        || canManageAnySessions || canAccessMarkingDefinitions,
    },
    {
      // Section root: redirects to asset_rules; Notifiers and Lessons learned
      // live in the Customization right submenu (OpenCTI-aligned), not as
      // direct entries.
      link: '/admin/settings/customization',
      label: 'Customization',
      userRight: hasTenantSettingsAccess || canAccessLessonsLearned,
    },
    {
      link: '/admin/settings/taxonomies',
      label: 'Taxonomies',
      userRight: hasTenantSettingsAccess || hasTagsAccess,
    },
    {
      link: '/admin/settings/data_ingestion',
      label: 'Data ingestion',
      userRight: hasTenantSettingsAccess,
    },
    {
      link: '/admin/settings/experience',
      label: 'Filigran Experience',
      userRight: hasTenantSettingsAccess || canAccessPlatformSettings,
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
