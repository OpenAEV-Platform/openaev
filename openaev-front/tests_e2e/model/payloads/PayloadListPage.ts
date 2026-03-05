import { type Locator, type Page } from '@playwright/test';

class PayloadListPage {
  readonly page: Page;
  readonly container: Locator;
  readonly addButton: Locator;
  readonly listContainer: Locator;
  readonly searchContainer: Locator;

  constructor(page: Page) {
    this.page = page;
    this.container = page.getByTestId('payload-list-page');
    this.addButton = page.getByRole('button', { name: 'Add' });
    this.listContainer = page.locator('.MuiListItem-root');
    this.searchContainer = page.getByPlaceholder('Search these results...');
  }

  getItem(lineNumber: number): Locator {
    return this.listContainer.nth(lineNumber);
  }

  async waitForLoad() {
    await this.container.waitFor({ state: 'visible' });
  }

  async openCreatePayload() {
    await this.addButton.click();
  }

  async searchPayload(search: string) {
    await this.searchContainer.fill(search);
  }
}

export default PayloadListPage;
