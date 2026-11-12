import { expect, type Locator, type Page } from '@playwright/test';

import { TIMEOUT } from '../../utils/constants';
class CatalogPage {
  constructor(private page: Page) {}
  async waitForLoad(): Promise<void> {
    await this.page.waitForURL('**/integrations/available**');
    await this.searchInput.waitFor({
      state: 'visible',
      timeout: TIMEOUT,
    });
    await Promise.any([
      this.page.getByTestId('marketplace-view-cards').first().waitFor({
        state: 'visible',
        timeout: TIMEOUT,
      }),
      this.page.getByTestId('marketplace-view-list').first().waitFor({
        state: 'visible',
        timeout: TIMEOUT,
      }),
    ]);
  }

  get searchInput(): Locator {
    return this.page.getByPlaceholder(/Search the catalog/i);
  }

  getConnectorCard(namePattern: string | RegExp): Locator {
    return this.page.getByTestId('connector-card').filter({ hasText: namePattern });
  }

  async searchConnector(text: string): Promise<void> {
    await this.searchInput.fill(text);
    await expect.poll(() => new URL(this.page.url()).searchParams.get('search'), { timeout: TIMEOUT }).toBe(text);
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
    return this.page.getByLabel('Display name', { exact: true });
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

  /**
   * Submits the connector-instance form and resolves once the instance is created.
   * Callers navigate away right after (to the deployed list): leaving before the
   * POST completes aborts it or reads the list before the instance is committed.
   */
  async submitInstall(): Promise<void> {
    const creation = this.page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/connector-instances'));
    await this.installButton.click();
    const response = await creation;
    expect(response.ok(), `Connector instance creation answered ${response.status()}`).toBe(true);
  }
}
export default CatalogPage;
