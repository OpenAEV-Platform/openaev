import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import CatalogPage from '../../model/integrations/CatalogPage';
import TenantSwitcherComponent from '../../model/nav/TenantSwitcherComponent';
import TenantsPage from '../../model/platform/TenantsPage';
import { TIMEOUT } from '../../utils/constants';
import { DEFAULT_TENANT_UUID, tenantUrl } from '../../utils/url';

/**
 * End-to-end test: install an external executor (Tanium) in a new tenant.
 */
test.describe('Catalog — external executor deployment', () => {
  const skipInCiWithoutLicense = Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE;
  if (skipInCiWithoutLicense) {
    return;
  }

  let newTenantId: string | null = null;
  let tenantName: string;

  const TANIUM_DISPLAY_NAME = `Tanium E2E ${Date.now()}`;

  test.beforeEach(async ({ page }) => {
    tenantName = `Tenant Executor E2E ${Date.now()}`;
    const tenantsPage = new TenantsPage(page);

    await page.goto(tenantUrl('/admin/settings/security/tenants'));
    await tenantsPage.waitForLoad();
    await expect(
      tenantsPage.createFabButton,
      'Enterprise Edition / multi-tenancy must be enabled: expected the tenant "Add" button to be visible.',
    ).toBeVisible({ timeout: TIMEOUT });

    await tenantsPage.openCreateDrawer();
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
  });

  test.afterEach(async ({ request }) => {
    if (newTenantId) {
      await new TenantApiHelpers(request).softDeleteTenant(newTenantId);
      newTenantId = null;
    }
  });

  test('should install Tanium executor and show it deployed', async ({ page }) => {
    expect(newTenantId).not.toBeNull();

    // Step: deploy Tanium from catalog, filling its mandatory configuration
    // (API URL, API key and package ids are required, so they render at the top
    // of the form rather than under "Advanced options").
    const catalogPage = new CatalogPage(page);
    await page.goto(tenantUrl('/admin/integrations/available', newTenantId!));
    await catalogPage.waitForLoad();

    await catalogPage.searchConnector('Tanium');
    // "Tanium" also matches the "Tanium Threat Response" collector; the catalog
    // sections collectors before executors, so target the exact executor title.
    await catalogPage.clickDeployOnConnector('Tanium Executor');
    await catalogPage.fillDisplayName(TANIUM_DISPLAY_NAME);
    await catalogPage.fillConfigurationField('Executor Tanium Api Url', 'https://tanium.e2e.invalid');
    await catalogPage.fillConfigurationField('Executor Tanium Api Key', 'e2e-api-key');
    await catalogPage.fillConfigurationField('Executor Tanium Windows Package Id', '1');
    await catalogPage.fillConfigurationField('Executor Tanium Unix Package Id', '2');
    await catalogPage.submitInstall();

    // Step: verify Tanium is installed on executors list
    await page.goto(tenantUrl('/admin/integrations/deployed', newTenantId!));
    await page.waitForURL('**/integrations/deployed**');

    const taniumCard = page.locator('.MuiCard-root').filter({ hasText: TANIUM_DISPLAY_NAME }).first();
    await expect(
      taniumCard,
      `Expected deployed Tanium executor card "${TANIUM_DISPLAY_NAME}" to be visible`,
    ).toBeVisible({ timeout: TIMEOUT });

    // Step: open executor and ensure it is deployed/running
    await taniumCard.click();
    await page.waitForURL('**/integrations/executors/**');

    const startButton = page.getByRole('button', { name: 'Start' }).first();
    await expect(startButton).toBeVisible({ timeout: TIMEOUT });
    await expect(page.getByText('Stopped', { exact: true })).toBeVisible({ timeout: TIMEOUT });
  });
});
