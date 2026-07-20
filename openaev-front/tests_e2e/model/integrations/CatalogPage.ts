import { type Locator, type Page } from '@playwright/test';
class CatalogPage {
  constructor(private page: Page) {}
  async waitForLoad(): Promise<void> {
    await this.page.waitForURL('**/integrations/available**');
  }

  get searchInput(): Locator {
    return this.page.getByPlaceholder('Search the catalog...');
  }

  getConnectorCard(namePattern: string | RegExp): Locator {
    return this.page.getByTestId('connector-card').filter({ hasText: namePattern });
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

  /**
   * Fills a required configuration field by its human-readable label. Labels are
   * derived from the configuration key by the form (e.g. EXECUTOR_TANIUM_API_URL
   * -> "Executor Tanium Api Url"). Uses a substring match to tolerate the
   * required-field marker ("*" for text/password, " *" for numbers).
   */
  async fillConfigurationField(label: string, value: string): Promise<void> {
    // Target the actual form control by role (text inputs and number inputs) so we
    // never resolve to a non-fillable wrapper element that merely carries the label.
    const control = this.page
      .getByRole('textbox', { name: label })
      .or(this.page.getByRole('spinbutton', { name: label }));
    await control.first().fill(value);
  }

  async submitInstall(): Promise<void> {
    await this.installButton.click();
  }
}
export default CatalogPage;
