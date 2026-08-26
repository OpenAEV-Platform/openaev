import { type Locator, type Page } from '@playwright/test';

class ThreatArsenalListPage {
  readonly page: Page;
  readonly addButton: Locator;
  readonly listContainer: Locator;
  readonly searchContainer: Locator;
  readonly gridViewButton: Locator;
  readonly listViewButton: Locator;
  readonly paginationRow: Locator;
  readonly filterField: Locator;
  readonly clearFiltersButton: Locator;

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
    this.filterField = page.getByPlaceholder('Add filter');
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear filters' });
  }

  async addFirstAvailableFilter() {
    await this.filterField.click();
    await this.page.getByRole('option').first().click();
  }

  async clearFilters() {
    await this.clearFiltersButton.click();
  }

  async switchToGridView() {
    await this.gridViewButton.click();
  }

  /** Keeps the branch out of the tests, which read better without one. */
  async switchToView(view: 'grid' | 'list') {
    await (view === 'grid' ? this.gridViewButton : this.listViewButton).click();
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
