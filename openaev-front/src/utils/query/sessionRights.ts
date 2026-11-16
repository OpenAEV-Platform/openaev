import { type User, type UserGrant } from '../api-types';

/** What decides which data the logged-in user may read. */
export interface SessionRights {
  admin: boolean;
  capabilities: string[];
  /** Resource id → grant. */
  grants: Record<string, UserGrant>;
}

export const rightsOf = (me: Pick<User, 'user_admin' | 'user_capabilities' | 'user_grants'>): SessionRights => ({
  admin: me.user_admin === true,
  capabilities: [...(me.user_capabilities ?? [])],
  grants: { ...me.user_grants },
});

const byKey = ([a]: [string, unknown], [b]: [string, unknown]) => {
  if (a === b) return 0;
  return a < b ? -1 : 1;
};

/** Equal for equal rights: the server returns capabilities and grants in no stable order. */
export const rightsKey = (rights: SessionRights): string => JSON.stringify([
  rights.admin,
  [...rights.capabilities].sort(),
  Object.entries(rights.grants).sort(byKey).map(([resourceId, { grant, type }]) => [resourceId, grant, type]),
]);

/**
 * Whether the user lost something they had: data cached with the previous rights may then no longer
 * be theirs to see. A changed grant counts as lost, whichever way it changed.
 */
export const rightsShrank = (previous: SessionRights, next: SessionRights): boolean =>
  (previous.admin && !next.admin)
  || previous.capabilities.some(capability => !next.capabilities.includes(capability))
  || Object.entries(previous.grants).some(([resourceId, { grant, type }]) =>
    next.grants[resourceId]?.grant !== grant || next.grants[resourceId]?.type !== type);

/** The query cache belongs to one user in one tenant: a change of either drops it. */
export const sessionCacheKey = (userId: string, tenantId: string | null | undefined): string => `${userId}|${tenantId ?? ''}`;
