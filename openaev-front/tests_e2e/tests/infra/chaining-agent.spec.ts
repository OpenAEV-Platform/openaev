import { expect, type Page } from '@playwright/test';

import { test } from '../../fixtures';
import ThreatArsenalHelper from '../../model/threat-arsenals/ThreatArsenalHelper';
import { installAgent, waitForRegisteredAgent } from '../../utils/agent';
import { tenantUrl } from '../../utils/url';

const createChainedScenario = async (page: Page, name: string): Promise<string> => {
  await page.goto(tenantUrl('/admin/scenarios'));
  await page.getByRole('button', {
    name: 'Create',
    exact: true,
  }).click();
  await page.getByRole('button', {
    name: 'Chained scenario',
    exact: true,
  }).click();
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByRole('button', {
    name: 'Create',
    exact: true,
  }).last().click();
  await page.waitForURL(/\/admin\/scenarios\/[0-9a-f-]+(?:\?.*)?$/);
  return page.url();
};

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
  await expect(page.getByText(hostname, { exact: true })).toBeVisible();
};

const addPayloadAction = async (page: Page, payloadName: string): Promise<void> => {
  await page.getByRole('tab', {
    name: 'Logic',
    exact: true,
  }).click();
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

test.describe.serial('Chained scenario attack path', () => {
  let hostname: string;
  let platform: string;
  const token = `chaining-e2e-${Date.now()}`;
  const payloadName = `E2E Chained Echo ${token}`;
  const scenarioName = `E2E Chained Scenario ${token}`;

  test.beforeAll(async ({ browser }) => {
    const installedAgent = await installAgent(browser);
    hostname = installedAgent.hostname;
    platform = installedAgent.platform;
  });

  test('launches a chained command and projects its execution in the attack path', async ({ page }) => {
    test.setTimeout(360_000);

    await waitForRegisteredAgent(page, hostname);
    const threatArsenalHelper = new ThreatArsenalHelper(page);
    await threatArsenalHelper.createCommandLinePayload({
      name: payloadName,
      command: `echo ${token}`,
      platform,
    });

    await createChainedScenario(page, scenarioName);
    await addEndpointToScope(page, hostname);
    await addPayloadAction(page, payloadName);
    const simulationUrl = await launchScenario(page);

    await expect(async () => {
      await page.goto(simulationUrl);

      const target = page.getByText(hostname, { exact: true }).first();
      const action = page.getByText(payloadName, { exact: true }).first();
      await expect(target).toBeVisible({ timeout: 10_000 });
      await expect(action).toBeVisible({ timeout: 10_000 });

      await action.click();
      const execution = page.getByRole('button', { name: new RegExp(payloadName, 'i') }).last();
      await expect(execution).toBeVisible({ timeout: 10_000 });
      await execution.click();
      await page.getByRole('tab', {
        name: 'Terminal view',
        exact: true,
      }).click();
      await expect(page.getByText(token, { exact: false })).toBeVisible({ timeout: 10_000 });
    }).toPass({
      intervals: [10_000],
      timeout: 240_000,
    });
  });
});
