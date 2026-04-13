import { APP_BASE_PATH } from './Environment';
import TENANT_MIGRATION_TODO from './tenant-api-migration';

/**
 * Base API path for tenant endpoints.
 * Defined here (not in tenant-action.ts) to avoid a dependency cycle:
 * tenant-url-helper → tenant-action → Action → tenant-url-helper.
 */
export const TENANT_URI = '/api/tenants';

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
 * Builds a full browser URL for a given tenant.
 *
 * When called without pathname / search / hash, the current
 * window.location values are used — this preserves deep links
 * during the initial tenant redirect (root.tsx).
 *
 * When called with explicit values (e.g. from useTenant during
 * a tenant switch), those values are used instead.
 *
 * @param tenantId  - target tenant UUID
 * @param pathname  - app-relative path (e.g. "/admin/scenarios"); defaults to current URL path
 * @param search    - query string (e.g. "?foo=bar"); defaults to current URL search
 * @param hash      - hash fragment (e.g. "#section"); defaults to current URL hash
 */
export const buildTenantUrl = (
  tenantId: string,
  pathname?: string,
  search?: string,
  hash?: string,
): string => {
  const base = APP_BASE_PATH || '';

  let resolvedPath: string;
  if (pathname !== undefined) {
    resolvedPath = pathname;
  } else {
    // Read the current deep link, stripping APP_BASE_PATH
    const raw = window.location.pathname;
    resolvedPath = raw.startsWith(base) ? raw.slice(base.length) : raw;
  }

  const normalizedPath = resolvedPath.startsWith('/') ? resolvedPath : `/${resolvedPath}`;
  const resolvedSearch = search ?? window.location.search;
  const resolvedHash = hash ?? window.location.hash;
  return `${base}/${tenantId}${normalizedPath}${resolvedSearch}${resolvedHash}`;
};

// ---------------------------------------------------------------------------
// Tenant ID resolution — URL only (no localStorage)
// ---------------------------------------------------------------------------

/**
 * Returns the current tenant ID from the URL pathname.
 * Falls back to DEFAULT_TENANT_UUID when the URL has no tenant segment
 * (e.g. public routes, early bootstrap before redirect).
 */
export const getCurrentTenantId = (): string => {
  return extractTenantFromUrl() ?? DEFAULT_TENANT_UUID;
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
