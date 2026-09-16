import { type APIRequestContext, expect, type Page } from '@playwright/test';

import ScenarioApiHelpers from '../../api-helpers/ScenarioApiHelpers';
import { test } from '../../fixtures';
import CatalogPage from '../../model/integrations/CatalogPage';
import InjectorInstancePage from '../../model/integrations/InjectorInstancePage';
import InjectorsListPage from '../../model/integrations/InjectorsListPage';
import ThreatArsenalHelper from '../../model/threat-arsenals/ThreatArsenalHelper';
import { installAgent, waitForRegisteredAgent } from '../../utils/agent';
import { tenantUrl } from '../../utils/url';

const NMAP_TCP_CONNECT_SCAN = 'Nmap - TCP Connect Scan';

// The creation drawer only renders its form once an engine card is picked, and
// chaining is gated behind Enterprise Edition. Creating the scenario over the
// API keeps this suite focused on the chaining behaviour it actually asserts.
const openChainedScenario = async (page: Page, request: APIRequestContext, name: string): Promise<void> => {
  const scenario = await new ScenarioApiHelpers(request).createScenario(name, true);
  expect(scenario.scenario_id, `Scenario "${name}" was not created`).toBeTruthy();
  await page.goto(tenantUrl(`/admin/scenarios/${scenario.scenario_id}`));
  await expect(page.getByRole('tab', {
    name: 'Logic',
    exact: true,
  })).toBeVisible();
};

// These selects are built from a bare InputLabel with no labelId, so the label
// is not programmatically tied to the combobox and getByLabel cannot find it.
const muiSelect = (page: Page, label: string) => page
  .locator('.MuiFormControl-root')
  .filter({ hasText: label })
  .getByRole('combobox')
  .first();

const attackPathNode = (page: Page, label: string) => page
  .getByTestId('attack-path-node')
  .filter({ hasText: label })
  .first();

const addEndpointToScope = async (page: Page, hostname: string): Promise<void> => {
  await page.getByRole('tab', {
    name: 'Scope',
    exact: true,
  }).click();
  await expect(page.getByText('Allow list', { exact: true })).toBeVisible();
  await page.getByRole('button', {
    name: 'Define',
    exact: true,
  }).first().click();
  await page.getByRole('tab', {
    name: 'Assets',
    exact: true,
  }).click();

  const endpoint = page.getByRole('button', { name: new RegExp(hostname, 'i') }).first();
  await expect(endpoint).toBeVisible();
  await endpoint.click();
  await page.getByRole('button', {
    name: 'Define scope',
    exact: true,
  }).click();
  await expect(page.getByText(hostname, { exact: true }).first()).toBeVisible();
};

const addPayloadAction = async (page: Page, payloadName: string): Promise<void> => {
  await page.getByRole('button', {
    name: 'Add component',
    exact: true,
  }).click();
  await page.getByRole('button', { name: /^Action\s/ }).click();
  await page.getByText(payloadName, { exact: true }).click();
  await page.getByRole('button', {
    name: 'Save',
    exact: true,
  }).click();
  await expect(page.getByRole('button', {
    name: 'Add component',
    exact: true,
  })).toBeVisible();
};

const addTextTrigger = async (page: Page, name: string, value: string): Promise<void> => {
  await page.getByRole('button', {
    name: 'Add component',
    exact: true,
  }).click();
  await page.getByRole('button', { name: /^Event\s/ }).click();
  // The field is required, so its accessible name carries a trailing asterisk.
  await page.locator('[name="event_name"]').fill(name);
  await muiSelect(page, 'Field to Check').click();
  await page.getByRole('option', {
    name: 'Text',
    exact: true,
  }).click();
  await muiSelect(page, 'Operator').click();
  await page.getByRole('option', {
    name: 'Equals',
    exact: true,
  }).click();
  await page.getByLabel('Expected Value', { exact: true }).fill(value);
  await page.getByRole('button', {
    name: 'Add trigger',
    exact: true,
  }).click();
  await expect(page.getByText(name, { exact: true }).first()).toBeVisible();
};

const addPayloadActionGatedByTrigger = async (page: Page, payloadName: string): Promise<void> => {
  await page.getByRole('button', {
    name: 'Add an action gated by this trigger',
    exact: true,
  }).click();
  await page.getByText(payloadName, { exact: true }).click();
  await page.getByRole('button', {
    name: 'Save',
    exact: true,
  }).click();
  await expect(page.getByRole('button', {
    name: 'Add component',
    exact: true,
  })).toBeVisible();
};

const deployNmapInjector = async (page: Page, name: string): Promise<void> => {
  const catalogPage = new CatalogPage(page);
  await page.goto(tenantUrl('/admin/integrations/available'));
  await catalogPage.waitForLoad();
  await catalogPage.searchConnector('Nmap');
  await catalogPage.clickDeployOnConnector('Nmap');
  await catalogPage.fillDisplayName(name);
  await catalogPage.submitInstall();

  const injectorsListPage = new InjectorsListPage(page);
  await page.goto(tenantUrl('/admin/integrations/deployed'));
  await injectorsListPage.waitForLoad();
  await injectorsListPage.waitForConnectorToAppear(name);
  await injectorsListPage.clickOnInjector(name);

  const injectorInstancePage = new InjectorInstancePage(page);
  await injectorInstancePage.waitForLoad();
  await injectorInstancePage.clickStart();
  await injectorInstancePage.waitForStarted();
};

const addNmapAction = async (page: Page, hostname: string): Promise<void> => {
  await page.getByRole('button', {
    name: 'Add component',
    exact: true,
  }).click();
  await page.getByRole('button', { name: /^Action\s/ }).click();
  await page.getByText(NMAP_TCP_CONNECT_SCAN, { exact: true }).click();
  await expect(page.getByText('Initial Target', { exact: true })).toBeVisible();
  await expect(page.getByText(hostname, { exact: true }).first()).toBeVisible();
  await page.getByRole('button', {
    name: 'Save',
    exact: true,
  }).click();
  await expect(page.getByRole('button', {
    name: 'Add component',
    exact: true,
  })).toBeVisible();
};

const launchScenario = async (page: Page): Promise<string> => {
  await page.getByRole('button', {
    name: 'Normal',
    exact: true,
  }).first().click();
  await page.getByRole('button', {
    name: 'Confirm',
    exact: true,
  }).click();
  await page.waitForURL(/\/admin\/simulations\/[0-9a-f-]+\/attack-path$/);
  return page.url();
};

test.describe.serial('Infrastructure - chaining', () => {
  let hostname: string;
  let platform: string;
  const runId = Date.now();
  // Kept out of the payload names: a token embedded in a name also shows up in
  // headings, edge labels and tooltips, which would make the assertions pass
  // without the command ever running.
  const sourceToken = `chainsrc${runId}`;
  const resultToken = `chainres${runId}`;
  const sourcePayloadName = `E2E Chain Source ${runId}`;
  const resultPayloadName = `E2E Chain Result ${runId}`;
  const scenarioName = `E2E Infra Chaining ${runId}`;
  const triggerName = `Source output received ${runId}`;

  test.beforeAll(async ({ browser }) => {
    const installedAgent = await installAgent(browser);
    hostname = installedAgent.hostname;
    platform = installedAgent.platform;
  });

  test('runs an output-triggered action chain and displays the resulting finding', async ({ page, request }) => {
    test.setTimeout(420_000);

    await waitForRegisteredAgent(page, hostname);
    const threatArsenalHelper = new ThreatArsenalHelper(page);
    await threatArsenalHelper.createCommandLinePayload({
      name: sourcePayloadName,
      command: `echo ${sourceToken}`,
      platform,
      textOutput: {
        name: 'Source token',
        key: 'source_token',
        rule: `(${sourceToken})`,
      },
    });
    await threatArsenalHelper.createCommandLinePayload({
      name: resultPayloadName,
      command: `echo ${resultToken}`,
      platform,
      textOutput: {
        name: 'Result token',
        key: 'result_token',
        rule: `(${resultToken})`,
      },
    });

    await openChainedScenario(page, request, scenarioName);
    await addEndpointToScope(page, hostname);
    await page.getByRole('tab', {
      name: 'Logic',
      exact: true,
    }).click();
    await addPayloadAction(page, sourcePayloadName);
    await addTextTrigger(page, triggerName, sourceToken);
    await addPayloadActionGatedByTrigger(page, resultPayloadName);
    const simulationUrl = await launchScenario(page);

    await expect(async () => {
      await page.goto(simulationUrl);

      // Node cards sit above the connector SVG, whose edge labels repeat the same
      // names and would swallow the click.
      const target = attackPathNode(page, hostname);
      const sourceAction = attackPathNode(page, sourcePayloadName);
      const resultAction = attackPathNode(page, resultPayloadName);
      await expect(target).toBeVisible({ timeout: 10_000 });
      await expect(sourceAction).toBeVisible({ timeout: 10_000 });
      await expect(resultAction).toBeVisible({ timeout: 10_000 });

      await resultAction.click();
      const resultExecution = page.getByRole('button', { name: new RegExp(resultPayloadName, 'i') }).last();
      await expect(resultExecution).toBeVisible({ timeout: 10_000 });
      await resultExecution.click();
      await page.getByRole('tab', {
        name: 'Terminal view',
        exact: true,
      }).click();
      await expect(page.getByText(resultToken).filter({ visible: true }).first()).toBeVisible({ timeout: 10_000 });

      await target.click();
      await expect(page.getByTitle(resultToken, { exact: true })).toBeVisible({ timeout: 10_000 });
    }).toPass({
      intervals: [10_000],
      timeout: 300_000,
    });
  });

  test('runs Nmap against the scoped agent endpoint and displays scan findings', async ({ page, request }) => {
    test.setTimeout(420_000);

    await waitForRegisteredAgent(page, hostname);
    const nmapInjectorName = `E2E Nmap ${Date.now()}`;
    const nmapScenarioName = `E2E Nmap Chaining ${Date.now()}`;

    await deployNmapInjector(page, nmapInjectorName);
    await openChainedScenario(page, request, nmapScenarioName);
    await addEndpointToScope(page, hostname);
    await page.getByRole('tab', {
      name: 'Logic',
      exact: true,
    }).click();
    await addNmapAction(page, hostname);
    const simulationUrl = await launchScenario(page);

    await expect(async () => {
      await page.goto(simulationUrl);

      const target = attackPathNode(page, hostname);
      const nmapAction = attackPathNode(page, NMAP_TCP_CONNECT_SCAN);
      await expect(target).toBeVisible({ timeout: 10_000 });
      await expect(nmapAction).toBeVisible({ timeout: 10_000 });

      await nmapAction.click();
      const execution = page.getByRole('button', { name: new RegExp(NMAP_TCP_CONNECT_SCAN, 'i') }).last();
      await expect(execution).toBeVisible({ timeout: 10_000 });
      await execution.click();
      await page.getByRole('tab', {
        name: 'Execution details',
        exact: true,
      }).click();
      await expect(page.getByText(/nmap\s+-Pn\s+-sT/i)).toBeVisible({ timeout: 10_000 });

      await page.getByRole('tab', {
        name: 'Findings',
        exact: true,
      }).click();
      await expect(page.getByText(/portscan|port/i).first()).toBeVisible({ timeout: 10_000 });
    }).toPass({
      intervals: [10_000],
      timeout: 300_000,
    });
  });
});
