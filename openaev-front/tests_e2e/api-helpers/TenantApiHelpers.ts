import { type APIRequestContext } from '@playwright/test';

class TenantApiHelpers {
  readonly tenantUri = '/api/tenants';

  constructor(private request: APIRequestContext) {}

  async softDeleteTenant(tenantId: string): Promise<void> {
    await this.request.delete(`${this.tenantUri}/${tenantId}`);
  }
}

export default TenantApiHelpers;
