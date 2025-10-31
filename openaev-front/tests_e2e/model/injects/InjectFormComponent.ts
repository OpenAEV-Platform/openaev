import type { Locator, Page } from '@playwright/test';

class InjectFormComponent {
  readonly page: Page;
  readonly updateTargetTeamButton: Locator;
  readonly allTeamButton: Locator;

  readonly saveButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.updateTargetTeamButton = this.page.getByRole('button', { name: 'Modify target teams' });
    this.saveButton = this.page.getByRole('button', { name: 'Update' });
    this.allTeamButton = this.page.getByRole('checkbox', { name: 'All teams' });
  }

  async save() {
    await this.saveButton.click();
  }

  async switchAllTeamsCheckbox() {
    await this.allTeamButton.click();
  }
}

export default InjectFormComponent;
