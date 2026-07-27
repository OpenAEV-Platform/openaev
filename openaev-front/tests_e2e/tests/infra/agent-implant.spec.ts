import { execSync } from 'node:child_process';
import os from 'node:os';

import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import AgentInstallPage from '../../model/agents/AgentInstallPage';
import EndpointListPage from '../../model/assets/EndpointListPage';
import AtomicTestingFormComponent from '../../model/atomic-testings/AtomicTestingFormComponent';
import AtomicTestingListPage from '../../model/atomic-testings/AtomicTestingListPage';
import ThreatArsenalHelper from '../../model/threat-arsenals/ThreatArsenalHelper';
import { AUTH_FILE } from '../../utils/constants';
import { tenantUrl } from '../../utils/url';

const APP_URL = process.env.APP_URL ?? 'http://localhost:3001';

const getOsPlatform = (): string => {
  switch (os.platform()) {
    case 'win32': return 'Windows';
    case 'darwin': return 'MacOS';
    default: return 'Linux';
  }
};

test.describe('Agent implant registration', () => {
  let hostname: string;
  const echoToken = `e2e-${Date.now()}`;
  const payloadName = `E2E Payload ${echoToken}`;

  test.beforeAll(async ({ browser }) => {
    hostname = os.hostname().toLowerCase();
    const platform = getOsPlatform();

    // Create an authenticated page to navigate the UI
    const context = await browser.newContext({
      storageState: AUTH_FILE,
      baseURL: APP_URL,
    });
    const page = await context.newPage();

    // ─── Install the agent ───
    await page.goto(tenantUrl('/admin/agents'));
    const agentInstallPage = new AgentInstallPage(page);
    await agentInstallPage.waitForLoad();
    const installCommand = await agentInstallPage.getInstallCommand(platform);

    // ─── Create the threat arsenal payload ───
    await page.goto(tenantUrl('/admin'));
    const threatArsenalHelper = new ThreatArsenalHelper(page);
    await threatArsenalHelper.createCommandLinePayload({
      name: payloadName,
      command: `echo ${echoToken}`,
      platform,
    });

    await context.close();

    // Windows PowerShell 5.1 prompts for confirmation when iwr parses HTML;
    // -UseBasicParsing avoids the interactive security prompt.
    const commandToExecute = os.platform() === 'win32'
      ? installCommand.replace(/\b(iwr|Invoke-WebRequest)\b/, '$1 -UseBasicParsing')
      : installCommand;

    // Execute the install command
    execSync(commandToExecute, {
      stdio: 'inherit',
      timeout: 60_000,
      shell: os.platform() === 'win32' ? 'powershell' : undefined,
    });
  });

  test('installed agent registers an endpoint', async ({ page }) => {
    // Poll the endpoints UI until the agent registers (up to 150 s)
    await expect(async () => {
      await page.goto(tenantUrl('/admin/assets'));
      const endpointList = new EndpointListPage(page);
      await endpointList.waitForLoad();
      await expect(endpointList.getEndpointByHostname(hostname)).toBeVisible();
    }).toPass({
      intervals: [5_000],
      timeout: 150_000,
    });
  });

  test('create and launch atomic test with payload on registered endpoint', async ({ page }) => {
    // Navigate to Atomic Testings
    await page.goto(tenantUrl('/admin/atomic_testings'));
    const atomicTestingList = new AtomicTestingListPage(page);
    await atomicTestingList.waitForLoad();
    await atomicTestingList.openCreateAtomicTesting();

    // Fill and submit the atomic test form
    const atomicTestingForm = new AtomicTestingFormComponent(page);
    await atomicTestingForm.searchAndSelectPayload(payloadName);
    await atomicTestingForm.selectAsset(hostname);
    await atomicTestingForm.submit();

    // Launch the atomic test
    await atomicTestingForm.launch();

    // In the redesigned atomic-testing detail the right-hand "Results by target"
    // panel is populated only once a target is selected in the left "Targets"
    // panel, and a page reload clears that selection. So on every poll iteration
    // we reload, (re-)open the Endpoints tab, select the endpoint row and check
    // whether the agent's execution traces have arrived yet (up to 240s).
    const endpointsTab = page.getByRole('tab', { name: 'Endpoints' });
    const endpointRow = page.getByRole('button', { name: new RegExp(hostname, 'i') });
    const spawnTrace = page.getByText('Implant spawn by the agent');

    await expect(async () => {
      await page.reload();
      if (await endpointsTab.isVisible().catch(() => false)) {
        await endpointsTab.click();
      }
      await endpointRow.first().click();
      // The START trace is only rendered once the agent has executed and reported.
      await expect(spawnTrace).toBeVisible({ timeout: 5_000 });
    }).toPass({
      intervals: [10_000],
      timeout: 240_000,
    });

    // Verify the attack command trace contains the echo output in stdout
    await expect(page.getByText(new RegExp(`"stdout":".*${echoToken}`))).toBeVisible();
  });
});
