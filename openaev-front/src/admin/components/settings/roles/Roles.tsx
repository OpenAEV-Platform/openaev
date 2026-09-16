import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import NoEnterpriseEdition from '../../../../utils/permissions/NoEnterpriseEdition';
import { CAPABILITY_SCOPES } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import RoleScopeProvider from './RoleScopeProvider';
import RolesTab from './RolesTab';

const Roles = () => {
  const { t } = useFormatter();
  const { scope, canAccessTenantUsers, canAccessPlatformUsers, isEnterpriseEdition } = useSecurityScope();
  const platformScope = scope === CAPABILITY_SCOPES.PLATFORM;
  // Keyed on the scope: switching it starts over from that scope's own list and stored query.
  const rolesTab = (
    <RoleScopeProvider key={scope} scope={scope}>
      <RolesTab />
    </RoleScopeProvider>
  );

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
        {!platformScope && canAccessTenantUsers && rolesTab}
        {platformScope && canAccessPlatformUsers && (isEnterpriseEdition ? rolesTab : <NoEnterpriseEdition />)}
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Roles;
