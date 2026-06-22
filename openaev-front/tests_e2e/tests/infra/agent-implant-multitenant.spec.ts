import { execSync } from 'node:child_process';
import os from 'node:os';

import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import AgentInstallPage from '../../model/agents/AgentInstallPage';
import AtomicTestingFormComponent from '../../model/atomic-testings/AtomicTestingFormComponent';
import AtomicTestingListPage from '../../model/atomic-testings/AtomicTestingListPage';
import TenantSwitcherComponent from '../../model/nav/TenantSwitcherComponent';
import TenantsPage from '../../model/platform/TenantsPage';
import ThreatArsenalHelper from '../../model/threat-arsenals/ThreatArsenalHelper';
import { normalizeAgentInstallCommand } from '../../utils/agentInstaller';
import { AUTH_FILE, TIMEOUT } from '../../utils/constants';
import { DEFAULT_TENANT_UUID, tenantUrl } from '../../utils/url';

const APP_URL = process.env.APP_URL ?? 'http://localhost:3001';

const getOsPlatform = (): string => {
  switch (os.platform()) {
    case 'win32': return 'Windows';
    case 'darwin': return 'MacOS';
    default: return 'Linux';
  }
};

/**
 * End-to-end test: OpenAEV agent executor running atomic test on a new tenant.
 */
test.describe('Multi-tenancy — agent on new tenant', () => {
  const skipInCiWithoutLicense = Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE;
  if (skipInCiWithoutLicense) {
    return;
  }

  let newTenantId: string | null = null;
  let hostname: string;
  let agentUser: string;
  const echoToken = `e2e-tenant-${Date.now()}`;
  const payloadName = `E2E Payload ${echoToken}`;

  test.beforeAll(async ({ browser }) => {
    hostname = os.hostname().toLowerCase();
    agentUser = execSync('whoami', { encoding: 'utf-8' }).trim();
    const platform = getOsPlatform();

    const context = await browser.newContext({
      storageState: AUTH_FILE,
      baseURL: APP_URL,
    });
    const page = await context.newPage();

    // ─── Create Tenant ───
    const tenantsPage = new TenantsPage(page);
    await page.goto(tenantUrl('/admin/settings/security/tenants'));
    await tenantsPage.waitForLoad();
    await expect(
      tenantsPage.createFabButton,
      'Enterprise Edition / multi-tenancy must be enabled.',
    ).toBeVisible({ timeout: TIMEOUT });
    await tenantsPage.openCreateDrawer();
    const tenantName = `Tenant Agent E2E ${Date.now()}`;
    await tenantsPage.fillTenantName(tenantName);
    await tenantsPage.submitCreate();

    const tenantSwitcher = new TenantSwitcherComponent(page);
    await tenantSwitcher.openSwitcher('Default');
    await expect(tenantSwitcher.popoverTenantItems.filter({ hasText: tenantName })).toHaveCount(1);
    await tenantSwitcher.selectTenantByName(tenantName);
    await page.waitForURL(
      url => !url.toString().includes(DEFAULT_TENANT_UUID),
      { timeout: TIMEOUT },
    );

    const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const segments = new URL(page.url()).pathname.split('/').filter(Boolean);
    newTenantId = segments.find(s => uuidPattern.test(s) && s !== DEFAULT_TENANT_UUID) ?? null;
    expect(newTenantId).not.toBeNull();

    // ─── Install the agent from the new tenant ───
    await page.goto(tenantUrl('/admin/agents', newTenantId!));
    const agentInstallPage = new AgentInstallPage(page);
    await agentInstallPage.waitForLoad();
    const installCommand = await agentInstallPage.getInstallCommand(platform);

    // ─── Create a command-line payload in the new tenant ───
    await page.goto(tenantUrl('/admin', newTenantId!));
    const threatArsenalHelper = new ThreatArsenalHelper(page);
    await threatArsenalHelper.createCommandLinePayload({
      name: payloadName,
      command: `echo ${echoToken}`,
      platform,
    });

    await context.close();

    // ─── Execute the install command ───
    const normalizedInstall = normalizeAgentInstallCommand(installCommand);
    execSync(normalizedInstall.command, {
      stdio: 'inherit',
      timeout: 60_000,
      shell: normalizedInstall.shell,
    });
  });

  test.afterAll(async ({ browser }) => {
    if (newTenantId) {
      const context = await browser.newContext({
        storageState: 'tests_e2e/.auth/user.json',
        baseURL: APP_URL,
      });
      await new TenantApiHelpers(context.request).softDeleteTenant(newTenantId);
      await context.close();
      newTenantId = null;
    }
  });

  test('should have OpenAEV agent executor running new atomic test on new tenant', async ({ page }) => {
    expect(newTenantId).not.toBeNull();

    // ─── Wait for agent to register an endpoint ───
    await expect(async () => {
      await page.goto(tenantUrl('/admin/assets/endpoints', newTenantId!));
      await page.waitForURL('**/assets/endpoints**');
      await expect(page.getByText(hostname)).toBeVisible();
    }).toPass({
      intervals: [5_000],
      timeout: 150_000,
    });

    // ─── Create and launch atomic test ───
    await page.goto(tenantUrl('/admin/atomic_testings', newTenantId!));
    const atomicTestingList = new AtomicTestingListPage(page);
    await atomicTestingList.waitForLoad();
    await atomicTestingList.openCreateAtomicTesting();

    const atomicTestingForm = new AtomicTestingFormComponent(page);
    await atomicTestingForm.searchAndSelectPayload(payloadName);
    await atomicTestingForm.selectAsset(hostname);
    await atomicTestingForm.submit();
    await atomicTestingForm.launch();

    // ─── Wait for execution result ───
    await expect(async () => {
      await page.reload();
      await expect(page.getByRole('button', { name: new RegExp(`${agentUser}.*Executed`, 'i') })).toBeVisible();
    }).toPass({
      intervals: [10_000],
      timeout: 240_000,
    });

    // Expand the agent row to reveal traces
    await page.getByRole('button', { name: new RegExp(`${agentUser}.*Executed`, 'i') }).click();
    await expect(page.getByText('Implant spawn by the agent')).toBeVisible();
    await expect(page.getByText(new RegExp(`"stdout":".*${echoToken}`))).toBeVisible();
  });
});
