import { createContext, useContext } from 'react';

import { createPlatformRole, deletePlatformRole, findPlatformRoles, searchPlatformRoles, updatePlatformRole } from '../../../../actions/platform/platform-role/platform-role-action';
import { PLATFORM_ROLE_SCHEMA_KEY } from '../../../../actions/platform/platform-role/platform-role-schema';
import { ROLE_SCHEMA_KEY } from '../../../../actions/roles/role-schema';
import { createRole, deleteRole, findRoles, searchRoles, updateRole } from '../../../../actions/roles/roles-actions';
import { ROLE_BASE_URL } from '../../../../constants/BaseUrls';
import { type RoleOutput } from '../../../../utils/api-types';
import { type CapabilityScope, SUBJECTS, type Subjects } from '../../../../utils/permissions/types';

export interface RoleScope {
  scope: CapabilityScope;
  schemaKey: typeof ROLE_SCHEMA_KEY | typeof PLATFORM_ROLE_SCHEMA_KEY;
  subject: Subjects;
  create: typeof createRole;
  update: typeof updateRole;
  remove: typeof deleteRole;
  search: typeof searchRoles;
  /** Resolves attached role ids: a selected role may be absent from the current search page. */
  find: (roleIds: string[]) => Promise<RoleOutput[]>;
  /** Own local storage entry, so both lists keep their own sort and filters. */
  storageKey: string;
  detailUrl: (roleId: string) => string;
}

export const ROLE_SCOPES = {
  TENANT: {
    scope: 'TENANT',
    schemaKey: ROLE_SCHEMA_KEY,
    subject: SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES,
    create: createRole,
    update: updateRole,
    remove: deleteRole,
    search: searchRoles,
    find: findRoles,
    storageKey: 'tenant_roles',
    detailUrl: (roleId: string) => `${ROLE_BASE_URL}/${roleId}`,
  },
  PLATFORM: {
    scope: 'PLATFORM',
    schemaKey: PLATFORM_ROLE_SCHEMA_KEY,
    subject: SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES,
    create: createPlatformRole,
    update: updatePlatformRole,
    remove: deletePlatformRole,
    search: searchPlatformRoles,
    find: (roleIds: string[]) => findPlatformRoles(roleIds).then((result: { data: RoleOutput[] }) => result.data ?? []),
    storageKey: 'platform_roles',
    detailUrl: (roleId: string) => `${ROLE_BASE_URL}/${roleId}?scope=platform`,
  },
} as const satisfies { [K in CapabilityScope]: RoleScope & { scope: K } };

export const RoleScopeContext = createContext<RoleScope | null>(null);

/** Throws outside a provider rather than defaulting to a scope: guessing here would grant the wrong rights. */
export const useRoleScope = (): RoleScope => {
  const roleScope = useContext(RoleScopeContext);
  if (!roleScope) {
    throw new Error('Role components must be mounted under a RoleScopeProvider');
  }
  return roleScope;
};
