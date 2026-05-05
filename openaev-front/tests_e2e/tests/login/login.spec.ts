import { expect } from '@playwright/test';

import { test } from '../../fixtures/baseFixtures';
import LoginPage from '../../model/login.page';
import appUrl from '../../utils/url';

test.describe('Login page', () => {
  test.use({
    storageState: {
      cookies: [],
      origins: [],
    },
  });

  test('should display the sign in button', async ({ page }) => {
    await page.goto(appUrl());

    const loginPage = new LoginPage(page);
    await expect(loginPage.getSignInButton()).toBeVisible();
  });
});
