/**
 * Default tenant UUID — must match Tenant.DEFAULT_TENANT_UUID on the backend
 * and DEFAULT_TENANT_UUID in src/utils/url-helper
 */
export const DEFAULT_TENANT_UUID = '2cffad3a-0001-4078-b0e2-ef74274022c3';

const appUrl = () => {
  return process.env.APP_URL ? process.env.APP_URL : 'http://localhost:3001';
};

/**
 * Builds a tenant-prefixed browser path for E2E navigation.
 * Example: tenantUrl('/admin/scenarios/abc') → '/{DEFAULT_TENANT_UUID}/admin/scenarios/abc'
 */
export const tenantUrl = (path: string, tenantId: string = DEFAULT_TENANT_UUID): string => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `/${tenantId}${normalizedPath}`;
};

export default appUrl;
