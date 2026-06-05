import { expect } from '@playwright/test';

import { test } from '../../../fixtures/baseFixtures';
import LoginPage from '../../../model/login.page';
import appUrl, { tenantUrl } from '../../../utils/url';

test.describe('Authentication flow', () => {
  test.use({
    storageState: {
      cookies: [],
      origins: [],
    },
  });

  test('should login and logout successfully', async ({ page }) => {
    // -- ARRANGE --
    const loginPage = new LoginPage(page);
    const username = process.env.E2E_USERNAME ?? 'admin@openaev.io';
    const password = process.env.E2E_PASSWORD ?? 'admin';

    await page.goto(appUrl());
    await expect(loginPage.getLoginPage()).toBeVisible();

    // -- ACT --
    await loginPage.getLoginInput().fill(username);
    await loginPage.getPasswordInput().fill(password);
    await loginPage.getSignInButton().click();

    // Ensure we are on an admin page where the top bar account menu is rendered.
    await page.goto(tenantUrl('/admin'));
    await expect(page).toHaveURL(/\/admin(?!\/login)/);

    // Trigger CsrfFilter once to ensure XSRF-TOKEN cookie exists, then logout with matching header.
    await page.evaluate(async () => {
      await fetch('/api/scenarios/search', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      }).catch(() => {});

      const tokenCookie = document.cookie
        .split('; ')
        .find(cookie => cookie.startsWith('XSRF-TOKEN='));
      const token = tokenCookie ? decodeURIComponent(tokenCookie.split('=')[1]) : '';

      await fetch('/logout', {
        method: 'POST',
        credentials: 'include',
        headers: token ? { 'X-XSRF-TOKEN': token } : {},
      });
    });
    await page.goto(appUrl());

    // -- ASSERT --
    await expect(loginPage.getSignInButton()).toBeVisible();
  });
});
