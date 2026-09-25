import { type Page } from '@playwright/test';

class TopBarPage {
  constructor(private page: Page) {
  }

  getAccountMenu() {
    return this.page.getByTestId('account-menu');
  }

  getLogoutEntryMenu() {
    return this.page.getByTestId('logout-item');
  }
}

export default TopBarPage;
