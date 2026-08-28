import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import NoEnterpriseEdition from '../../../../utils/permissions/NoEnterpriseEdition';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import PlatformUsersTab from './platform_users/PlatformUsersTab';
import TenantUsersTab from './tenant_users/TenantUsersTab';

const Users = () => {
  const { t } = useFormatter();
  const { scope, canAccessTenantUsers, canAccessPlatformUsers, isEnterpriseEdition } = useSecurityScope();
  const platformScope = scope === 'PLATFORM';

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Security') }, { label: platformScope ? t('Platform') : t('This tenant') }, {
            label: t('Users'),
            current: true,
          }]}
        />
        {!platformScope && canAccessTenantUsers && <TenantUsersTab />}
        {platformScope && canAccessPlatformUsers && (isEnterpriseEdition ? <PlatformUsersTab /> : <NoEnterpriseEdition />)}
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Users;
