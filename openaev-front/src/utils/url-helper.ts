import { APP_BASE_PATH } from './Environment';

// ---------------------------------------------------------------------------
// Tenant URI
// ---------------------------------------------------------------------------

/**
 * Base API path for tenant endpoints.
 * Defined here (not in tenant-action.ts) to avoid a dependency cycle:
 * url-helper → tenant-action → Action → url-helper.
 */
export const TENANT_URI = '/api/tenants';

/**
 * Default tenant UUID used as fallback when no tenant has been selected yet.
 * Must match Tenant.DEFAULT_TENANT_UUID on the backend.
 */
export const DEFAULT_TENANT_UUID = '2cffad3a-0001-4078-b0e2-ef74274022c3';

export const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Strips entity-specific detail segments from a path so that a tenant switch
 * lands on the parent list page rather than a detail page for a resource
 * that may not exist in the target tenant.
 *
 * e.g. "/admin/scenarios/123e4567-e89b-12d3-a456-426614174000"
 *        → "/admin/scenarios"
 *      "/admin/scenarios/123e4567-e89b-12d3-a456-426614174000/injects"
 *        → "/admin/scenarios"
 *      "/admin/scenarios" → "/admin/scenarios" (unchanged)
 */
export const stripDetailSegments = (pathname: string): string => {
  const segments = pathname.split('/').filter(Boolean);
  const uuidIndex = segments.findIndex(s => UUID_REGEX.test(s));
  if (uuidIndex === -1) return pathname;
  return '/' + segments.slice(0, uuidIndex).join('/');
};

// ---------------------------------------------------------------------------
// URL helpers
// ---------------------------------------------------------------------------

/**
 * Returns the current pathname stripped of APP_BASE_PATH.
 */
const getAppRelativePath = (): string => {
  const base = APP_BASE_PATH || '';
  const raw = window.location.pathname;
  return raw.startsWith(base) ? raw.slice(base.length) : raw;
};

// ---------------------------------------------------------------------------
// URL helpers — reading tenant from the browser URL
// ---------------------------------------------------------------------------

/**
 * Extracts the tenant UUID from the current URL pathname.
 * Returns null when the first path segment is not a UUID
 * (e.g. public routes like /login, /comcheck/…, /reset).
 */
export const extractTenantFromUrl = (): string | null => {
  const segments = getAppRelativePath().split('/').filter(Boolean);
  if (segments.length >= 1 && UUID_REGEX.test(segments[0])) {
    return segments[0];
  }
  return null;
};

/**
 * Builds the BrowserRouter basename for tenant mode.
 * Appends the tenant UUID from the URL to the base path.
 * Falls back to the base path alone when no tenant UUID is present.
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
  const resolvedSearch = search ?? (pathname !== undefined ? '' : window.location.search);
  const resolvedHash = hash ?? (pathname !== undefined ? '' : window.location.hash);
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
  '/api/ai',
  '/api/login',
  '/api/auth',
  '/api/reset',
  '/api/settings',
  '/api/tenants',
  '/api/logs',
  '/api/platform-groups',
  '/api/platform-roles',
  '/api/platform-users',
  '/api/capabilities',
  '/api/xtmhub/contact-us',
  '/api/xtmhub/auto-register',
  '/api/xtm-composer',
  '/api/stream',
  '/api/schemas',
  '/api/engine',
];

/**
 * API path patterns (regex) that are NEVER tenant-scoped.
 * Used for platform endpoints whose prefix overlaps with tenant-scoped ones.
 */
const TENANT_EXEMPT_PATTERNS = [
  /^\/api\/users\/[^/]+\/password$/,
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
  if (TENANT_EXEMPT_PATTERNS.some(pattern => pattern.test(uri))) {
    return uri;
  }

  const tenantId = getCurrentTenantId();
  const pathAfterApi = uri.slice('/api'.length);
  return `/api/tenants/${tenantId}${pathAfterApi}`;
};
