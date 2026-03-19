/**
 * Default tenant UUID used as fallback until multi-tenancy frontend is fully implemented.
 * TODO: replace with dynamic tenant from user context / URL.
 */
export const DEFAULT_TENANT_UUID = '2cffad3a-0001-4078-b0e2-ef74274022c3';

/**
 * Builds a tenant-scoped API URI.
 *
 * @param path - the resource path (e.g. '/tags', '/scenarios')
 * @param tenantId - optional tenant ID, defaults to DEFAULT_TENANT_UUID
 * @returns the full tenant-scoped URI (e.g. '/api/tenants/{tenantId}/tags')
 */
export const tenantUri = (path: string, tenantId: string = DEFAULT_TENANT_UUID): string =>
  `/api/tenants/${tenantId}${path}`;

