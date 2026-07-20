import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import NoEnterpriseEdition from '../../../../utils/permissions/NoEnterpriseEdition';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import PlatformRolesTab from './platform_roles/PlatformRolesTab';
import TenantRolesTab from './tenant_roles/TenantRolesTab';

const Roles = () => {
  const { t } = useFormatter();
  const { scope, canAccessTenant, canAccessPlatform, isEnterpriseEdition } = useSecurityScope();
  const platformScope = scope === 'platform';

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Security') }, { label: platformScope ? t('Platform') : t('This tenant') }, {
            label: t('Roles'),
            current: true,
          }]}
        />
        {!platformScope && canAccessTenant && <TenantRolesTab />}
        {platformScope && canAccessPlatform && (isEnterpriseEdition ? <PlatformRolesTab /> : <NoEnterpriseEdition />)}
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Roles;
