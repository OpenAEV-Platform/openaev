import type { APIRequestContext } from '@playwright/test';
import { expect } from '@playwright/test';

import { test } from '../../../fixtures/baseFixtures';
import LoginPage from '../../../model/login.page';
import appUrl, { tenantUrl } from '../../../utils/url';

function backendUrl(): string {
  return process.env.BACKEND_URL ?? 'http://localhost:8080';
}

async function readAuditLogFromActuator(request: APIRequestContext): Promise<string> {
  const response = await request.get(`${backendUrl()}/actuator/logfile`, { headers: { Accept: 'text/plain' } });
  expect(response.ok()).toBeTruthy();
  return response.text();
}

test.describe('Authentication flow', () => {
  test.use({
    storageState: {
      cookies: [],
      origins: [],
    },
  });

  test('should login and logout successfully and emit audit log entries', async ({ page, request }) => {
    // -- ARRANGE --
    const loginPage = new LoginPage(page);
    const username = process.env.E2E_USERNAME ?? 'admin@openaev.io';
    const password = process.env.E2E_PASSWORD ?? 'admin';

    // Capture current logfile length so assertions only target new content written during this test.
    const sizeBefore = (await readAuditLogFromActuator(request)).length;

    await page.goto(appUrl());
    await expect(loginPage.getLoginPage()).toBeVisible();

    // -- ACT --
    await loginPage.getLoginInput().fill(username);
    await loginPage.getPasswordInput().fill(password);
    await loginPage.getSignInButton().click();

    // Ensure we are on an admin page (the URL assertion below prevents /admin/login redirects)
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

    // -- ASSERT: UI flow --
    await expect(loginPage.getSignInButton()).toBeVisible();

    // -- ASSERT: audit log endpoint must contain login and logout entries within 5s --
    await expect
      .poll(async () => {
        const content = await readAuditLogFromActuator(request);
        return content.slice(sizeBefore);
      }, {
        timeout: 5000,
        message: 'Expected login audit entry in /actuator/logfile',
      })
      .toContain('"event_scope" : "login"');

    await expect
      .poll(async () => {
        const content = await readAuditLogFromActuator(request);
        return content.slice(sizeBefore);
      }, {
        timeout: 5000,
        message: 'Expected logout audit entry in /actuator/logfile',
      })
      .toContain('"event_scope" : "logout"');
  });
});
