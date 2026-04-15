import { type Locator, type Page } from '@playwright/test';

class ThreatArsenalListPage {
  readonly page: Page;
  readonly addButton: Locator;
  readonly listContainer: Locator;
  readonly searchContainer: Locator;

  constructor(page: Page) {
    this.page = page;
    this.addButton = page.getByRole('button', { name: 'Add' });
    this.listContainer = page.locator('.MuiListItem-root');
    this.searchContainer = page.getByPlaceholder('Search these results...');
  }

  getItem(lineNumber: number): Locator {
    return this.listContainer.nth(lineNumber);
  }

  async waitForLoad() {
    await this.page.waitForURL('**/threat-arsenal**');
  }

  async openCreateThreatArsenal() {
    await this.addButton.click();
  }

  async searchThreatArsenal(search: string) {
    await this.searchContainer.fill(search);
  }
}

export default ThreatArsenalListPage;
