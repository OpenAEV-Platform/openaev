import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import NoEnterpriseEdition from '../../../../utils/permissions/NoEnterpriseEdition';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import PlatformGroupsTab from './platform_groups/PlatformGroupsTab';
import TenantGroupsTab from './tenant_groups/TenantGroupsTab';

const Groups = () => {
  const { t } = useFormatter();
  const { scope, canAccessTenant, canAccessPlatform, isEnterpriseEdition } = useSecurityScope();
  const platformScope = scope === 'platform';

  return (
    <div style={{
      display: 'flex',
      overflow: 'auto',
    }}
    >
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Settings') }, { label: t('Security') }, { label: platformScope ? t('Platform') : t('This tenant') }, {
            label: t('Groups'),
            current: true,
          }]}
        />
        {!platformScope && canAccessTenant && <TenantGroupsTab />}
        {platformScope && canAccessPlatform && (isEnterpriseEdition ? <PlatformGroupsTab /> : <NoEnterpriseEdition />)}
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Groups;
