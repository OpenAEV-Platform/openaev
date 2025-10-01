import { type Page } from '@playwright/test';

class TopBarPage {
  constructor(private page: Page) {
  }

  getAccountMenu() {
    return this.page.getByLabel('account-menu');
  }

  getLogoutEntryMenu() {
    return this.page.getByLabel('logout-item');
  }
}

export default TopBarPage;
