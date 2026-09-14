import { type Locator, type Page } from '@playwright/test';

import { TIMEOUT } from '../../utils/constants';

class InjectorsListPage {
  constructor(private page: Page) {}

  async waitForLoad(): Promise<void> {
    await this.page.waitForURL('**/integrations/deployed**');
    await this.page
      .getByTestId('marketplace-view-cards')
      .or(this.page.getByTestId('marketplace-view-list'))
      .first()
      .waitFor({
        state: 'visible',
        timeout: TIMEOUT,
      });
  }

  get searchInput(): Locator {
    return this.page.getByPlaceholder('Search deployed integrations...');
  }

  getInjectorCard(namePattern: string | RegExp): Locator {
    return this.page
      .getByTestId('connector-card')
      .filter({ hasText: namePattern })
      .or(
        this.page
          .getByTestId('connector-line')
          .filter({ hasText: namePattern }),
      );
  }

  async searchInjector(text: string): Promise<void> {
    await this.searchInput.fill(text);
  }

  /**
   * Waits until an injector card with the given name appears in the list.
   * Useful after catalog deployment when the connector instance is created asynchronously.
   */
  async waitForConnectorToAppear(injectorName: string, timeout = TIMEOUT): Promise<void> {
    await this.getInjectorCard(injectorName).first().waitFor({
      state: 'visible',
      timeout,
    });
  }

  /** Click on the injector card to navigate to its detail page */
  async clickOnInjector(injectorName: string): Promise<void> {
    const card = this.getInjectorCard(new RegExp(injectorName, 'i'));
    await card.first().waitFor({ state: 'visible' });
    await card.first().click();
  }
}

export default InjectorsListPage;
