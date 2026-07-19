import { useContext } from 'react';
import { useLocation, useSearchParams } from 'react-router';

import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

export type SecurityScope = 'tenant' | 'platform';

// The tenant / platform scope of the Security section is driven by the URL
// (`?scope=` query param for the entities existing in both scopes, pathname
// for scope-exclusive pages) and chosen from the single context switcher on
// top of the security right-menu, so it is shareable and back-button friendly.

// Pages that only exist in one scope pin the section to that scope.
const PLATFORM_ONLY_PATHS = ['/admin/settings/security/tenants'];
interface UseSecurityScope {
  scope: SecurityScope;
  canAccessTenant: boolean;
  canAccessPlatform: boolean;
  isEnterpriseEdition: boolean;
}

const useSecurityScope = (): UseSecurityScope => {
  const ability = useContext(AbilityContext);
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const canAccessTenant = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const canAccessPlatform = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES);

  const [searchParams] = useSearchParams();
  const location = useLocation();
  const requested = PLATFORM_ONLY_PATHS.some(path => location.pathname.startsWith(path))
    ? 'platform'
    : searchParams.get('scope');

  // Honor the requested scope when the user has access to it, otherwise fall
  // back to whichever scope is available (tenant first).
  let scope: SecurityScope;
  if (requested === 'platform' && canAccessPlatform) {
    scope = 'platform';
  } else if (requested === 'tenant' && canAccessTenant) {
    scope = 'tenant';
  } else {
    scope = canAccessTenant ? 'tenant' : 'platform';
  }

  return {
    scope,
    canAccessTenant,
    canAccessPlatform,
    isEnterpriseEdition,
  };
};

export default useSecurityScope;
