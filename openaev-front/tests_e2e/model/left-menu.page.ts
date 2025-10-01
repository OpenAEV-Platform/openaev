import { type Page } from '@playwright/test';

class LeftMenuPage {
  constructor(private page: Page) {
  }

  goToAssets() {
    return this.page.getByLabel('Assets').click();
  }

  goToContracts() {
    return this.page.getByLabel('Integrations').click();
  }
}

export default LeftMenuPage;
