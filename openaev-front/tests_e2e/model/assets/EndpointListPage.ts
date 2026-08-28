import { type Locator, type Page } from '@playwright/test';

class EndpointListPage {
  readonly page: Page;
  readonly listContainer: Locator;

  constructor(page: Page) {
    this.page = page;
    this.listContainer = page.getByRole('listitem');
  }

  async waitForLoad() {
    await this.page.waitForURL('**/admin/assets**');
  }

  getEndpointByHostname(hostname: string): Locator {
    return this.listContainer.filter({ hasText: hostname });
  }
}

export default EndpointListPage;
