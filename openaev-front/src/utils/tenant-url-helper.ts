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
// API path rewriting — AOP tenant prefix for backend API calls
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
];

/**
 * API prefixes NOT YET migrated to /api/tenants/{tenantId}/… on the backend.
 *
 * As each BE controller is migrated, remove the corresponding prefix(es).
 * Once this list is empty, all tenant-scoped APIs are fully migrated.
 *
 * PR1 — Tags ✅ DONE
 * PR2 — Scenarios & Exercises core
 * PR3 — Injects & Inject lifecycle
 * PR4 — Teams & Players
 * PR5 — Assets
 * PR6 — Components (Channels, Challenges, Payloads, Documents)
 * PR7 — Findings, Expectations & Lessons
 * PR8 — Integrations (Injectors, Collectors, Executors, Connectors)
 * PR9 — Reference data & Misc
 */
const TENANT_MIGRATION_TODO: string[] = [
  // PR2 — Scenarios & Exercises core
  '/api/scenarios',
  '/api/exercises',
  '/api/simulations',
  // PR3 — Injects & Inject lifecycle
  '/api/injects',
  '/api/injector_contracts',
  '/api/atomic-testings',
  '/api/inject-expectations-traces',
  // PR4 — Teams & Players
  '/api/teams',
  '/api/players',
  '/api/organizations',
  // PR5 — Assets
  '/api/endpoints',
  '/api/asset_groups',
  '/api/security_platforms',
  // PR6 — Components
  '/api/channels',
  '/api/challenges',
  '/api/payloads',
  '/api/documents',
  // PR7 — Findings, Expectations & Lessons
  '/api/findings',
  '/api/detection-remediations',
  '/api/notification-rules',
  '/api/vulnerabilities',
  // PR8 — Integrations
  '/api/injectors',
  '/api/collectors',
  '/api/executors',
  '/api/connector-instances',
  '/api/catalog-connector',
  // PR9 — Reference data & Misc
  '/api/attack_patterns',
  '/api/kill_chain_phases',
  '/api/domains',
  '/api/mappers',
  '/api/tag-rules',
  '/api/dashboards',
  '/api/fulltextsearch',
  '/api/roles',
  '/api/groups',
  '/api/users',
  '/api/capabilities',
  '/api/xtmhub',
  '/api/variables',
  '/api/reports',
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
