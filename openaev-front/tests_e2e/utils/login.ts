import { expect, type Page } from '@playwright/test';

import LoginPage from '../model/login.page';
import TopBarPage from '../model/topBar.page';
import appUrl from './url';

const login = async (page: Page) => {
  await page.goto(appUrl());

  const loginPage = new LoginPage(page);
  await expect(loginPage.getLoginPage()).toBeVisible();

  await loginPage.getLoginInput().fill('admin@openaev.io');
  await loginPage.getPasswordInput().fill('admin');
  await loginPage.getSignInButton().click();

  const topBarPage = new TopBarPage(page);
  await expect(topBarPage.getAccountMenu()).toBeVisible();
};

export default login;
