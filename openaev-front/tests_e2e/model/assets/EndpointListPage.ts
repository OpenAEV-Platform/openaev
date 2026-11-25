import { type Locator, type Page } from '@playwright/test';

import MuiListHelpers from '../../utils/MuiListHelpers';

class EndpointListPage {
  readonly page: Page;
  readonly searchField: Locator;

  constructor(page: Page) {
    this.page = page;
    this.searchField = page.getByPlaceholder('Search these results...').first();
  }

  async waitForLoad() {
    await this.page.waitForURL('**/admin/assets**');
  }

  /** Resolves once the list was searched with `text`, so the rows shown are its results. */
  async search(text: string) {
    const searched = this.page.waitForResponse(response => response.url().includes('/search')
      && (response.request().postData() ?? '').includes(text));
    await this.searchField.fill(text);
    await searched;
  }

  getEndpointByName(name: string): Locator {
    return MuiListHelpers.filterItemsInList(this.page, name);
  }
}

export default EndpointListPage;
