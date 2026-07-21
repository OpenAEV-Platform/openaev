import { type Locator, type Page } from '@playwright/test';

class TenantsPage {
  constructor(private page: Page) {}

  /** Top-right "Create" button that opens the create-tenant drawer */
  get createFabButton(): Locator {
    return this.page.getByTestId('button-create');
  }

  /** "Name" text field inside the TenantForm drawer */
  get tenantNameInput(): Locator {
    return this.page.getByLabel('Name*', { exact: true });
  }

  /** "Create" submit button inside the TenantForm drawer */
  get createSubmitButton(): Locator {
    return this.page.locator('#tenantFormId').getByRole('button', { name: 'Create' });
  }

  async waitForLoad(): Promise<void> {
    await this.page.waitForURL('**/security/tenants**');
  }

  async openCreateDrawer(): Promise<void> {
    await this.createFabButton.click();
  }

  async fillTenantName(name: string): Promise<void> {
    await this.tenantNameInput.fill(name);
  }

  async submitCreate(): Promise<void> {
    await this.createSubmitButton.click();
  }
}

export default TenantsPage;
