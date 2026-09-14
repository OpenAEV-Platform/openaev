import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import CatalogPage from '../../model/integrations/CatalogPage';
import InjectorsListPage from '../../model/integrations/InjectorsListPage';
import { TIMEOUT } from '../../utils/constants';
import { tenantUrl } from '../../utils/url';

/**
 * End-to-end test: install an external executor (Tanium) in a new tenant.
 */
test.describe('Catalog — external executor deployment', () => {
  const skipInCiWithoutLicense = Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE;
  if (skipInCiWithoutLicense) {
    return;
  }

  let newTenantId: string | null = null;

  const TANIUM_DISPLAY_NAME = `Tanium E2E ${Date.now()}`;

  test.beforeEach(async ({ request }) => {
    const tenantName = `Tenant Executor E2E ${Date.now()}`;
    const createdTenant = await new TenantApiHelpers(request).createTenant(tenantName);
    newTenantId = createdTenant.tenant_id;
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
    await expect(async () => {
      await page.goto(tenantUrl('/admin/integrations/available', newTenantId!));
      await catalogPage.waitForLoad();
      await catalogPage.searchConnector('Tanium');
      // "Tanium" also matches the "Tanium Threat Response" collector; the catalog
      // sections collectors before executors, so target the exact executor title.
      await catalogPage.clickDeployOnConnector('Tanium Executor');
      await expect(catalogPage.displayNameInput).toBeVisible({ timeout: TIMEOUT });
    }).toPass({
      intervals: [1_000, 2_000, 5_000],
      timeout: 6 * TIMEOUT,
    });

    await catalogPage.fillDisplayName(TANIUM_DISPLAY_NAME);
    await catalogPage.fillConfigurationField('Executor Tanium Api Url', 'https://tanium.e2e.invalid');
    await catalogPage.fillConfigurationField('Executor Tanium Api Key', 'e2e-api-key');
    await catalogPage.fillConfigurationField('Executor Tanium Windows Package Id', '1');
    await catalogPage.fillConfigurationField('Executor Tanium Unix Package Id', '2');
    await catalogPage.submitInstall();

    // Step: verify Tanium is installed on executors list
    const deployedConnectorsPage = new InjectorsListPage(page);
    await expect(async () => {
      await page.goto(tenantUrl('/admin/integrations/deployed', newTenantId!));
      await deployedConnectorsPage.waitForLoad();
      await expect(
        deployedConnectorsPage.getInjectorCard(TANIUM_DISPLAY_NAME).first(),
        `Expected deployed Tanium executor card "${TANIUM_DISPLAY_NAME}" to be visible`,
      ).toBeVisible({ timeout: TIMEOUT });
    }).toPass({
      intervals: [1_000, 2_000, 5_000],
      timeout: 6 * TIMEOUT,
    });

    const taniumCard = deployedConnectorsPage.getInjectorCard(TANIUM_DISPLAY_NAME).first();

    // Step: open executor and ensure it is deployed/running
    await taniumCard.click();
    await page.waitForURL('**/integrations/executors/**');

    const startButton = page.getByRole('button', { name: 'Start' }).first();
    await expect(startButton).toBeVisible({ timeout: TIMEOUT });
    await expect(page.getByText('Stopped', { exact: true })).toBeVisible({ timeout: TIMEOUT });
  });
});
