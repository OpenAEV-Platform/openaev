import { execSync } from 'node:child_process';
import os from 'node:os';

import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import { tenantUrl } from '../../utils/url';

const APP_URL = process.env.APP_URL ?? 'http://localhost:8080';
const ADMIN_TOKEN = process.env.OPENAEV_ADMIN_TOKEN!;

const getOsPlatform = (): string => {
  switch (os.platform()) {
    case 'win32': return 'Windows';
    case 'darwin': return 'MacOS';
    default: return 'Linux';
  }
};

test.describe('Agent implant registration', () => {
  let hostname: string;

  test.beforeAll(async ({ browser }) => {
    expect(ADMIN_TOKEN, 'OPENAEV_ADMIN_TOKEN must be set').toBeTruthy();
    hostname = os.hostname().toLowerCase();
    const platform = getOsPlatform();

    // Create an authenticated page to navigate the UI
    const context = await browser.newContext({
      storageState: 'tests_e2e/.auth/user.json',
      baseURL: APP_URL,
    });
    const page = await context.newPage();

    // Navigate to the agents page and open the first executor (OpenAEV Agent)
    await page.goto(tenantUrl('/admin/agents'));
    await page.waitForURL('**/agents**');
    await page.getByRole('button', { name: /Install/i }).first().click();

    // Select the platform matching the current OS
    await page.getByText(`Install ${platform} agent`).click();

    // Grab the install command from the <pre> block
    const preBlock = page.locator('pre').first();
    await expect(preBlock).not.toBeEmpty();
    const installCommand = (await preBlock.textContent())!;

    await context.close();

    // Execute the install command
    execSync(installCommand, {
      stdio: 'inherit',
      timeout: 60_000,
    });
  });

  test('installed agent registers an endpoint', async ({ page }) => {
    // Poll the endpoints API until the agent registers (up to 150 s)
    await expect(async () => {
      const res = await page.request.get(`${APP_URL}/api/endpoints`, { headers: { Authorization: `Bearer ${ADMIN_TOKEN}` } });
      expect(res.ok()).toBeTruthy();
      const endpoints: { endpoint_hostname: string }[] = await res.json();
      const match = endpoints.some(
        e => e.endpoint_hostname.toLowerCase() === hostname,
      );
      expect(match, `No endpoint with hostname "${hostname}" found yet`).toBeTruthy();
    }).toPass({
      intervals: [5_000],
      timeout: 150_000,
    });

    // Verify the endpoint is visible in the UI
    await page.goto(tenantUrl('/admin/assets/endpoints'));
    await page.waitForURL('**/assets/endpoints**');

    const endpointRow = page.getByRole('listitem').filter({ hasText: hostname });
    await expect(endpointRow).toBeVisible();
  });
});
