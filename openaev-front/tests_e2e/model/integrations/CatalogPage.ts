import { type Locator, type Page } from '@playwright/test';
class CatalogPage {
  constructor(private page: Page) {}
  async waitForLoad(): Promise<void> {
    await this.page.waitForURL('**/integrations/catalog**');
  }

  get searchInput(): Locator {
    return this.page.getByPlaceholder('Search these results...');
  }

  getConnectorCard(namePattern: string | RegExp): Locator {
    return this.page.locator('.MuiCard-root').filter({ hasText: namePattern });
  }

  async searchConnector(text: string): Promise<void> {
    await this.searchInput.fill(text);
  }

  /**
   * Finds the catalog card whose title matches connectorTitle (case-insensitive)
   * and clicks the "Deploy" button on it.
   */
  async clickDeployOnConnector(connectorTitle: string): Promise<void> {
    const card = this.getConnectorCard(new RegExp(connectorTitle, 'i'));
    await card.first().waitFor({ state: 'visible' });
    await card.first().getByRole('button', { name: 'Deploy' }).click();
  }

  /** "Display name" field in the CreateConnectorInstanceDrawer form */
  get displayNameInput(): Locator {
    return this.page.getByLabel('Display name*', { exact: true });
  }

  /** Submit button (labelled "Create") inside the connector-instance form */
  get installButton(): Locator {
    return this.page.locator('#connectorInstanceForm').getByRole('button', { name: 'Create' });
  }

  async fillDisplayName(name: string): Promise<void> {
    await this.displayNameInput.fill(name);
  }

  async submitInstall(): Promise<void> {
    await this.installButton.click();
  }
}
export default CatalogPage;
