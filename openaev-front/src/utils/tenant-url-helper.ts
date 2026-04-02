import { TENANT_URI } from '../actions/platform/tenants/tenant-action';
import { APP_BASE_PATH } from './Environment';

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

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// ---------------------------------------------------------------------------
// URL helpers — reading tenant from the browser URL
// ---------------------------------------------------------------------------

/**
 * Extracts the tenant UUID from the current URL pathname.
 * Returns null when the first path segment is not a UUID
 * (e.g. public routes like /login, /comcheck/…, /reset).
 */
export const extractTenantFromUrl = (): string | null => {
  const base = APP_BASE_PATH || '';
  const pathname = window.location.pathname.startsWith(base)
    ? window.location.pathname.slice(base.length)
    : window.location.pathname;
  const segments = pathname.split('/').filter(Boolean);
  if (segments.length >= 1 && UUID_REGEX.test(segments[0])) {
    return segments[0];
  }
  return null;
};

/**
 * Builds the BrowserRouter basename including the tenant prefix
 * when the current URL contains a tenant UUID segment.
 */
export const computeTenantBasename = (): string => {
  const base = APP_BASE_PATH || '';
  const tenantId = extractTenantFromUrl();
  return tenantId ? `${base}/${tenantId}` : base;
};

/**
 * Builds a full browser URL for a given tenant and in-app pathname.
 * Used when switching tenants or hard-redirecting to a tenant-prefixed URL.
 *
 * @param tenantId  - target tenant UUID
 * @param pathname  - app-relative path (e.g. "/admin/scenarios"), should start with "/"
 * @param search    - optional query string (e.g. "?foo=bar")
 * @param hash      - optional hash fragment (e.g. "#section")
 */
export const buildTenantUrl = (
  tenantId: string,
  pathname: string,
  search: string = '',
  hash: string = '',
): string => {
  const base = APP_BASE_PATH || '';
  const normalizedPath = pathname.startsWith('/') ? pathname : `/${pathname}`;
  return `${base}/${tenantId}${normalizedPath}${search}${hash}`;
};

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
// API URI helper — building tenant-scoped backend API paths
// ---------------------------------------------------------------------------

/**
 * Builds a tenant-scoped API URI using the active tenant from local storage.
 * Example: buildTenantApiUri('/tags') → '/api/tenants/<tenantId>/tags'
 */
export const buildTenantApiUri = (path: string): string =>
  `${TENANT_URI}/${getCurrentTenantId()}${path}`;