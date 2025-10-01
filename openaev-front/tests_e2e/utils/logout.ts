import { type Page } from '@playwright/test';

import TopBarPage from '../model/topBar.page';

const logout = async (page: Page) => {
  const topBarPage = new TopBarPage(page);

  await topBarPage.getAccountMenu().click();
  await topBarPage.getLogoutEntryMenu().click();
};

export default logout;
