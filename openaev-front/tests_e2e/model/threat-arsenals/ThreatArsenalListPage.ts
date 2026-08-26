import { type Locator, type Page } from '@playwright/test';

class ThreatArsenalListPage {
  readonly page: Page;
  readonly addButton: Locator;
  readonly listContainer: Locator;
  readonly searchContainer: Locator;
  readonly gridViewButton: Locator;
  readonly listViewButton: Locator;
  readonly paginationRow: Locator;

  constructor(page: Page) {
    this.page = page;
    this.addButton = page.getByTestId('button-create');
    this.listContainer = page.getByTestId('threat-arsenal-card');
    // The redesigned list uses the shared pagination search (SearchFilter),
    // whose default placeholder is "Search these results...".
    this.searchContainer = page.getByPlaceholder('Search these results...');
    this.gridViewButton = page.getByRole('button', { name: 'Grid view' });
    this.listViewButton = page.getByRole('button', { name: 'List view' });
    this.paginationRow = page.locator('.MuiTablePagination-root');
  }

  async switchToGridView() {
    await this.gridViewButton.click();
  }

  async switchToListView() {
    await this.listViewButton.click();
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
