import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import ThreatArsenalHelper from '../../model/threat-arsenals/ThreatArsenalHelper';
import { installAgent } from '../../utils/agent';
import { AUTH_FILE } from '../../utils/constants';
import { tenantUrl } from '../../utils/url';

const APP_URL = process.env.APP_URL ?? 'http://localhost:8080';
const ADMIN_TOKEN = process.env.OPENAEV_ADMIN_TOKEN!;

test.describe('Agent implant registration', () => {
  let hostname: string;
  const payloadName = `E2E Payload ${Date.now()}`;

  test.beforeAll(async ({ browser }) => {
    expect(ADMIN_TOKEN, 'OPENAEV_ADMIN_TOKEN must be set').toBeTruthy();
    const installedAgent = await installAgent(browser);
    hostname = installedAgent.hostname;

    const context = await browser.newContext({
      storageState: AUTH_FILE,
      baseURL: APP_URL,
    });

    try {
      const page = await context.newPage();
      await page.goto(tenantUrl('/admin'));
      await new ThreatArsenalHelper(page).createCommandLinePayload({
        name: payloadName,
        command: 'echo \'this is a test\'',
        platform: installedAgent.platform,
      });
    } finally {
      await context.close();
    }
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

  test('create and launch atomic test with payload on registered endpoint', async ({ page }) => {
    // Navigate to Atomic Testings
    await page.goto(tenantUrl('/admin/atomic_testings'));
    await page.waitForURL('**/atomic_testings**');

    // Open the create atomic test drawer
    await page.getByRole('button', { name: 'Create' }).click();

    // Search and select the payload we created
    await page.getByPlaceholder('Search').first().fill(payloadName);
    await page.getByText(payloadName).first().click();

    // Add the registered endpoint as a target
    await page.getByText('Modify assets').click();
    await page.getByText(hostname, { exact: false }).first().click();
    await page.getByRole('button', { name: 'Submit' }).click();

    // Submit the atomic test creation
    await page.getByRole('button', { name: 'Create' }).click();

    // Wait for navigation to the atomic testing detail page
    await page.waitForURL('**/atomic_testings/**');

    // Launch the atomic test
    await page.getByRole('button', { name: /Launch now/i }).click();
    // Confirm the launch dialog
    await page.getByRole('button', { name: /Launch/i }).last().click();

    // Navigate to the "Inject Execution details" tab to see traces
    await page.getByRole('tab', { name: /Execution details/i }).click();

    // Wait for the agent to execute and send back results (up to 120s)
    await expect(async () => {
      await page.reload();
      await page.getByRole('tab', { name: /Execution details/i }).click();
      const traces = page.getByText('Traces');
      await expect(traces).toBeVisible();
      // Verify that execution traces contain content (not just the heading)
      const traceContent = page.locator('text=SUCCESS').or(page.locator('text=FAILED'));
      await expect(traceContent.first()).toBeVisible();
    }).toPass({
      intervals: [10_000],
      timeout: 120_000,
    });
  });
});
