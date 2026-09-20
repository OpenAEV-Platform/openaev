import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import AtomicTestingFormComponent from '../../model/atomic-testings/AtomicTestingFormComponent';
import AtomicTestingListPage from '../../model/atomic-testings/AtomicTestingListPage';
import ThreatArsenalHelper from '../../model/threat-arsenals/ThreatArsenalHelper';
import { installAgent, waitForRegisteredAgent } from '../../utils/agent';
import { AUTH_FILE } from '../../utils/constants';
import { tenantUrl } from '../../utils/url';

const APP_URL = process.env.APP_URL ?? 'http://localhost:3001';

test.describe.serial('Agent implant registration', () => {
  let hostname: string;
  const echoToken = `e2e-${Date.now()}`;
  const payloadName = `E2E Payload ${echoToken}`;

  test.beforeAll(async ({ browser }) => {
    const installedAgent = await installAgent(browser);
    hostname = installedAgent.hostname;

    // Create an authenticated page to navigate the UI
    const context = await browser.newContext({
      storageState: AUTH_FILE,
      baseURL: APP_URL,
    });

    try {
      const page = await context.newPage();
      await page.goto(tenantUrl('/admin'));
      const threatArsenalHelper = new ThreatArsenalHelper(page);
      await threatArsenalHelper.createCommandLinePayload({
        name: payloadName,
        command: `echo ${echoToken}`,
        platform: installedAgent.platform,
      });
    } finally {
      await context.close();
    }
  });

  // Assertion is performed by waitForRegisteredAgent.
  // eslint-disable-next-line playwright/expect-expect
  test('installed agent registers an endpoint', async ({ page }) => {
    await waitForRegisteredAgent(page, hostname);
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
