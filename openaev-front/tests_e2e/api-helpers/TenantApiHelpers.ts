import { type APIRequestContext } from '@playwright/test';

import { TIMEOUT } from '../utils/constants';

/**
 * Tenant creation cascades through every onboarding dependency manager (queue
 * provisioning, per-tenant migrations and datapacks) before it answers, far past
 * the 15s UI fail-fast default of playwright.config.ts. Same budget as the
 * tenant-management spec gives the creation round trip.
 */
const TENANT_CREATION_TIMEOUT = 3 * TIMEOUT;

interface TenantApiOutput {
  tenant_id: string;
  tenant_name: string;
}

class TenantApiHelpers {
  readonly tenantUri = '/api/tenants';

  constructor(private request: APIRequestContext) {}

  async createTenant(tenantName?: string): Promise<TenantApiOutput> {
    const data = { tenant_name: tenantName ?? `Tenant E2E ${Date.now()}` };
    const response = await this.request.post(this.tenantUri, {
      data,
      timeout: TENANT_CREATION_TIMEOUT,
    });

    if (!response.ok()) {
      throw new Error(
        `Failed to create tenant (POST ${this.tenantUri}): ${response.status()} ${response.statusText()} - ${await response.text()}`,
      );
    }

    return (await response.json()) as TenantApiOutput;
  }

  async softDeleteTenant(tenantId: string): Promise<void> {
    await this.request.delete(`${this.tenantUri}/${tenantId}`);
  }
}

export default TenantApiHelpers;
