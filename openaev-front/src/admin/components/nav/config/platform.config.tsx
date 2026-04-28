import { DeviceHubOutlined } from '@mui/icons-material';

import { type LeftMenuItem } from '../../../../components/common/menu/leftmenu/leftmenu-model';
import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import EEChip from '../../common/entreprise_edition/EEChip';
import { SETTINGS_PATH } from '../../platform/settings/routes/SettingsRoutes';
import { TENANTS_PATH } from '../../platform/tenants/routes/TenantsRoutes';
import { PLATFORM_USERS_CAPABILITIES_ROUTE } from '../../platform/users_capabilities/users-capabilities-constants';

export const PLATFORM_ROUTE = '/admin/platform';
export const PLATFORM_PARAMETERS_ROUTE = `${PLATFORM_ROUTE}/${SETTINGS_PATH}`;
export const PLATFORM_TENANTS_ROUTE = `${PLATFORM_ROUTE}/${TENANTS_PATH}`;

const platformEntries = (ability: AppAbility): LeftMenuItem[] => {
  // Standard hooks
  const { t } = useFormatter();
  const { isValidated: isValidatedEnterpriseEdition } = useEnterpriseEdition();
  const isMultiTenancyEnabled = isFeatureEnabled('MULTI_TENANCY');

  return [
    {
      path: PLATFORM_ROUTE,
      icon: () => (<DeviceHubOutlined />),
      label: 'Platform',
      href: 'platform',
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS)
        || (isMultiTenancyEnabled && ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS))
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
          chip: !isValidatedEnterpriseEdition
            ? (
                <EEChip
                  clickable
                  featureDetectedInfo={t('TENANTS')}
                />
              )
            : undefined,
          userRight: isMultiTenancyEnabled && ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS),
        },
        {
          link: PLATFORM_USERS_CAPABILITIES_ROUTE,
          label: 'Users & capabilities',
          chip: !isValidatedEnterpriseEdition
            ? (
                <EEChip
                  clickable
                  featureDetectedInfo={t('Users & capabilities')}
                />
              )
            : undefined,
          userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
        },
      ],
    },
  ];
};

export default platformEntries;
