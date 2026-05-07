import {
  DomainOutlined,
  GroupsOutlined,
  HomeWorkOutlined,
  LocalPoliceOutlined,
  PermIdentityOutlined,
  SecurityOutlined,
} from '@mui/icons-material';
import { type FunctionComponent, memo, useContext } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';
import EEChip from '../common/entreprise_edition/EEChip';

const SecurityMenuComponent: FunctionComponent = () => {
  const { isValidated: isEnterpriseEdition, openDialog } = useEnterpriseEdition();
  const ability = useContext(AbilityContext);
  const isMultiTenancyEnabled = isFeatureEnabled('MULTI_TENANCY');
  const canAccessTenantSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const canAccessPlatformUGR = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES) && isMultiTenancyEnabled;
  const canAccessTenants = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS);

  const entries: RightMenuEntry[] = [
    // Roles, Groups, Users: visible if tenant or platform access
    ...(canAccessTenantSettings || canAccessPlatformUGR
      ? [
          {
            path: '/admin/settings/security/roles',
            icon: () => (<SecurityOutlined />),
            label: 'Roles',
          },
          {
            path: '/admin/settings/security/groups',
            icon: () => (<GroupsOutlined />),
            label: 'Groups',
          },
          {
            path: '/admin/settings/security/users',
            icon: () => (<PermIdentityOutlined />),
            label: 'Users',
          },
        ]
      : []),
    // Policies & Organizations: tenant-only
    ...(canAccessTenantSettings
      ? [
          {
            path: '/admin/settings/security/policies',
            icon: () => (<LocalPoliceOutlined />),
            label: 'Policies',
          },
          {
            path: '/admin/settings/security/organizations',
            icon: () => (<DomainOutlined />),
            label: 'Organizations',
          },
        ]
      : []),
    // Tenants: platform-only + multi-tenancy
    ...(isMultiTenancyEnabled && canAccessTenants
      ? [{
          path: '/admin/settings/security/tenants',
          icon: () => (<HomeWorkOutlined />),
          label: 'Tenants',
          chip: !isEnterpriseEdition ? (<EEChip clickable />) : undefined,
          onClick: !isEnterpriseEdition ? () => openDialog() : undefined,
        }]
      : []),
  ];
  return (
    <RightMenu entries={entries} />
  );
};

const SecurityMenu = memo(SecurityMenuComponent);

export default SecurityMenu;
