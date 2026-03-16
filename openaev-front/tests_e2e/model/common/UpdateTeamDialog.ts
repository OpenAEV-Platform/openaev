import type { Locator, Page } from '@playwright/test';

class UpdateTeamDialog {
  readonly page: Page;
  readonly searchField: Locator;
  readonly listContainer: Locator;
  readonly createNewTeamButton: Locator;
  readonly saveButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.searchField = page.getByRole('textbox', { name: 'Search these results...' });
    this.listContainer = page.getByTestId('select-team-list');
    this.createNewTeamButton = page.getByRole('button', { name: 'Create a new team' });
    this.saveButton = page.getByRole('button', { name: 'Update' });
  }

  // -- Action methods

  async save() {
    await this.saveButton.click();
  };

  async createNewTeam(name: string, description = '', isContextual = false) {
    await this.createNewTeamButton.click();
    await this.page.getByRole('textbox', { name: 'Name' }).fill(name);
    if (description) {
      await this.page.getByRole('textbox', { name: 'Description' }).fill(description);
    }
    if (isContextual) {
      await this.page.getByRole('checkbox', { name: 'Only in this context' }).check();
    }
    await this.page.getByTestId('team-form-submit-button').click();
  }
}

export default UpdateTeamDialog;
