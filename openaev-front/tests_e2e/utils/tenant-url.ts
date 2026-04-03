/**
 * E2E helper — builds a tenant-prefixed URL for page.goto() calls.
 *
 * Without this, navigating to e.g. `/admin/scenarios/<id>` triggers a
 * hard redirect in root.tsx to add the tenant prefix, which causes an
 * extra full-page reload. Using a tenant-prefixed URL directly avoids
 * the redirect and makes tests faster and more reliable.
 *
 * The tenant UUID can be overridden via E2E_TENANT_ID env var.
 * Falls back to DEFAULT_TENANT_UUID (matches backend's Tenant.DEFAULT_TENANT_UUID).
 */
const DEFAULT_TENANT_UUID = '2cffad3a-0001-4078-b0e2-ef74274022c3';

const tenantUrl = (path: string): string => {
  const tenantId = process.env.E2E_TENANT_ID ?? DEFAULT_TENANT_UUID;
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return `/${tenantId}${normalized}`;
};

export default tenantUrl;

