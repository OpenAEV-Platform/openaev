import { useCallback, useContext } from 'react';
import { useLocation, useSearchParams } from 'react-router';

import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, type CapabilityScope, SUBJECTS } from '../../../utils/permissions/types';

export type SecurityScope = CapabilityScope;

// The tenant / platform scope of the Security section is driven by the URL
// (`?scope=` query param for the entities existing in both scopes, pathname
// for scope-exclusive pages) and chosen from the single context switcher on
// top of the security right-menu, so it is shareable and back-button friendly.

// Pages that only exist in one scope pin the section to that scope.
const PLATFORM_ONLY_PATHS = ['/admin/settings/security/tenants'];

const SESSION_SUBJECT = {
  TENANT: SUBJECTS.SESSIONS,
  PLATFORM: SUBJECTS.PLATFORM_SESSIONS,
} as const;

interface UseSecurityScope {
  scope: SecurityScope;
  /** Access to the tenant scope through any of its capabilities. */
  canAccessTenant: boolean;
  /** Tenant settings only: organizations, policies. */
  canAccessTenantSettings: boolean;
  /** Tenant users, groups and roles, granted independently of the settings. */
  canAccessTenantUsers: boolean;
  /** Reachability of the platform scope, whatever the capability behind it. */
  canAccessPlatform: boolean;
  /** Platform users, groups and roles specifically. */
  canAccessPlatformUsers: boolean;
  /** Sessions management in the given scope. */
  canAccessSession: (forScope: SecurityScope) => boolean;
  isEnterpriseEdition: boolean;
}

const useSecurityScope = (): UseSecurityScope => {
  const ability = useContext(AbilityContext);
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const canAccessSession = useCallback(
    (forScope: SecurityScope) => ability.can(ACTIONS.MANAGE, SESSION_SUBJECT[forScope]),
    [ability],
  );
  const canAccessTenantSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const canAccessTenantUsers = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES);
  const canAccessPlatformUsers = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES);
  const canAccessTenant = canAccessTenantSettings || canAccessTenantUsers || canAccessSession('TENANT');
  const canAccessPlatform = canAccessPlatformUsers || canAccessSession('PLATFORM');

  const [searchParams] = useSearchParams();
  const location = useLocation();
  const requested = PLATFORM_ONLY_PATHS.some(path => location.pathname.startsWith(path))
    ? 'PLATFORM'
    : searchParams.get('scope')?.toUpperCase();

  // Honor the requested scope when the user has access to it, otherwise fall
  // back to whichever scope is available (tenant first). The platform scope is
  // an EE feature, so a `?scope=platform` request is ignored in Community
  // Edition (where the scope switcher is not displayed).
  let scope: SecurityScope;
  if (requested === 'PLATFORM' && canAccessPlatform && isEnterpriseEdition) {
    scope = 'PLATFORM';
  } else if (requested === 'TENANT' && canAccessTenant) {
    scope = 'TENANT';
  } else {
    scope = canAccessTenant ? 'TENANT' : 'PLATFORM';
  }

  return {
    scope,
    canAccessTenant,
    canAccessTenantSettings,
    canAccessTenantUsers,
    canAccessPlatform,
    canAccessPlatformUsers,
    canAccessSession,
    isEnterpriseEdition,
  };
};

export default useSecurityScope;
