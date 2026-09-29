import { type APIRequestContext } from '@playwright/test';

interface TenantApiOutput {
  tenant_id: string;
  tenant_name: string;
}

class TenantApiHelpers {
  readonly tenantUri = '/api/tenants';

  constructor(private request: APIRequestContext) {}

  async createTenant(tenantName?: string): Promise<TenantApiOutput> {
    const data = { tenant_name: tenantName ?? `Tenant E2E ${Date.now()}` };
    const response = await this.request.post(this.tenantUri, { data });

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
