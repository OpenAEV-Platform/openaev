import TENANT_MIGRATION_TODO from './tenant-api-migration';

/**
 * Local-storage key used to persist the selected tenant.
 * Shared between tenant-url-helper and useTenant hook.
 */
export const TENANT_STORAGE_KEY = 'current-tenant-storage';

/**
 * Default tenant UUID used as fallback when no tenant has been selected yet.
 * Must match Tenant.DEFAULT_TENANT_UUID on the backend.
 */
export const DEFAULT_TENANT_UUID = '2cffad3a-0001-4078-b0e2-ef74274022c3';

// ---------------------------------------------------------------------------
// Tenant ID resolution — reading tenant from local storage
// ---------------------------------------------------------------------------

/**
 * Reads the current tenant ID from local storage.
 * Falls back to DEFAULT_TENANT_UUID when nothing is stored.
 */
export const getCurrentTenantId = (): string => {
  try {
    const tenantRaw = localStorage.getItem(TENANT_STORAGE_KEY);
    if (tenantRaw) {
      const tenant = JSON.parse(tenantRaw);
      if (tenant?.tenant_id) {
        return tenant.tenant_id;
      }
    }
  } catch {
    // malformed JSON — fall back
  }
  return DEFAULT_TENANT_UUID;
};

// ---------------------------------------------------------------------------
// API path rewriting
// ---------------------------------------------------------------------------

/**
 * API path prefixes that are NEVER tenant-scoped (platform-global endpoints).
 */
const TENANT_EXEMPT_PREFIXES = [
  '/api/me',
  '/api/login',
  '/api/auth',
  '/api/reset',
  '/api/settings',
  '/api/tenants',
  '/api/logs',
  '/api/images',
  '/api/platform-groups',
  '/api/platform-roles',
];

/**
 * Rewrites an API path to include the tenant prefix.
 *
 * This is the FE equivalent of the BE's TenantInterceptor:
 * one place that applies the tenant prefix to all API calls.
 */
export const buildTenantApiPath = (uri: string): string => {
  if (!uri.startsWith('/api/')) {
    return uri;
  }
  if (TENANT_EXEMPT_PREFIXES.some(prefix => uri.startsWith(prefix))) {
    return uri;
  }
  if (TENANT_MIGRATION_TODO.some(prefix => uri.startsWith(prefix))) {
    return uri;
  }

  const tenantId = getCurrentTenantId();
  const pathAfterApi = uri.slice('/api'.length);
  return `/api/tenants/${tenantId}${pathAfterApi}`;
};
