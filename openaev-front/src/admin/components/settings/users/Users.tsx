import { Box, Tab, Tabs } from '@mui/material';
import { useContext, useState } from 'react';

import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import NoEnterpriseEdition from '../../../../utils/permissions/NoEnterpriseEdition';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import EEChip from '../../common/entreprise_edition/EEChip';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import PlatformUsersTab from './platform_users/PlatformUsersTab';
import TenantUsersTab from './tenant_users/TenantUsersTab';

const Users = () => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);
  const { isValidated: isEnterpriseEdition, openDialog } = useEnterpriseEdition();
  const canAccessTenant = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const canAccessPlatform = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES) && isFeatureEnabled('MULTI_TENANCY');
  const defaultTab = canAccessTenant ? 'tenant' : 'platform';
  const [currentTab, setCurrentTab] = useState(() => localStorage.getItem('settings_users_tab') ?? defaultTab);
  const handleTabChange = (val: string) => {
    if (val === 'platform' && !isEnterpriseEdition) {
      openDialog();
      return;
    }
    localStorage.setItem('settings_users_tab', val);
    setCurrentTab(val);
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Security') }, {
            label: t('Users'),
            current: true,
          }]}
        />
        <Box sx={{
          borderBottom: 1,
          borderColor: 'divider',
          marginBottom: 2,
        }}
        >
          <Tabs
            value={currentTab}
            onChange={(_, val) => handleTabChange(val)}
          >
            {canAccessTenant && <Tab label="Tenant" value="tenant" />}
            {canAccessPlatform && (
              <Tab
                label={t('Platform')}
                value="platform"
                icon={!isEnterpriseEdition ? <EEChip clickable /> : undefined}
                iconPosition="end"
                sx={{ gap: 1 }}
              />
            )}
          </Tabs>
        </Box>
        {currentTab === 'tenant' && canAccessTenant && <TenantUsersTab />}
        {currentTab === 'platform' && canAccessPlatform && (isEnterpriseEdition ? <PlatformUsersTab /> : <NoEnterpriseEdition />)}
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Users;
