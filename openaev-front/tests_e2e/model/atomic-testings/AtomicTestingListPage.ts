import { type Locator, type Page } from '@playwright/test';

class AtomicTestingListPage {
  readonly page: Page;
  readonly addButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.addButton = page.getByTestId('button-create');
  }

  async waitForLoad() {
    await this.page.waitForURL('**/atomic_testings**');
  }

  async openCreateAtomicTesting() {
    await this.addButton.click();
  }
}

export default AtomicTestingListPage;
