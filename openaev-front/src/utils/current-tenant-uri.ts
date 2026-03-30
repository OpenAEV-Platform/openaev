import { TENANT_URI } from '../actions/platform/tenants/tenant-action';
import { TENANT_STORAGE_KEY } from './hooks/useTenant';

/**
 * Default tenant UUID used as fallback when no tenant has been selected yet.
 * Must match Tenant.DEFAULT_TENANT_UUID on the backend.
 */
export const DEFAULT_TENANT_UUID = '2cffad3a-0001-4078-b0e2-ef74274022c3';

/**
 * Reads the current tenant ID from local storage.
 * Falls back to DEFAULT_TENANT_UUID when nothing is stored.
 */
const getCurrentTenantId = (): string => {
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

/**
 * Builds a tenant-scoped API URI using the active tenant from local storage.
 */
export const currentTenantUri = (path: string): string =>
  `${TENANT_URI}/${getCurrentTenantId()}${path}`;
