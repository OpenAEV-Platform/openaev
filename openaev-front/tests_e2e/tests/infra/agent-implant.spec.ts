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
  const payloadName = `E2E Payload ${Date.now()}`;

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

    // Create the threat arsenal payload (Command Line for Linux)
    await page.goto(tenantUrl('/admin'));
    await page.getByRole('menuitem', { name: 'Threat Arsenal' }).click();
    await page.waitForURL('**/threat-arsenal**');
    await page.getByRole('button', { name: 'Add' }).click();

    // Fill General tab
    await page.getByRole('textbox', { name: 'Name*' }).fill(payloadName);
    await page.getByRole('combobox', { name: 'Domains' }).click();
    await page.getByRole('option', { name: 'Endpoint' }).click();
    await page.keyboard.press('Escape');

    // Switch to Commands tab
    await page.getByRole('tab', { name: 'Commands' }).click();
    await page.getByRole('combobox', { name: 'Type *' }).click();
    await page.getByRole('option', { name: 'Command Line' }).click();
    await page.getByRole('combobox', { name: 'Platforms' }).click();
    await page.getByRole('option', { name: 'Linux' }).click();
    await page.keyboard.press('Escape');
    await page.getByRole('combobox', { name: 'Executor *' }).click();
    await page.getByRole('option', { name: 'bash' }).click();
    await page.locator('textarea[name="command_content"]').fill('echo \'this a test\'');

    // Save the payload
    await page.getByRole('tab', { name: 'General' }).click();
    await page.getByRole('button', { name: 'Create' }).click();
    await expect(page.getByText('The element has been successfully created')).toBeVisible();

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
