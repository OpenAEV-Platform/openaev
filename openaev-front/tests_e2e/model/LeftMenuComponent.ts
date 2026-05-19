import { type Page } from '@playwright/test';

class LeftMenuComponent {
  constructor(private page: Page) {
  }

  goToAssets() {
    return this.page.getByLabel('Assets').click();
  }

  goToContracts() {
    return this.page.getByLabel('Integrations').click();
  }

  goToThreatArsenal() {
    return this.page.getByRole('menuitem', { name: 'Threat Arsenal' }).click();
  };

  async goToTeams() {
    await this.page.getByRole('menuitem', { name: 'People' }).click();
    return this.page.getByRole('menuitem', { name: 'Players' }).click();
  }
}

export default LeftMenuComponent;
