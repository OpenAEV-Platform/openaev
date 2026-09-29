import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import CatalogPage from '../../model/integrations/CatalogPage';
import InjectorsListPage from '../../model/integrations/InjectorsListPage';
import { TIMEOUT } from '../../utils/constants';
import { tenantUrl } from '../../utils/url';

/**
 * End-to-end test: install an external collector (Atomic Red Team) in a new tenant.
 */
test.describe('Catalog — external collector deployment', () => {
  const skipInCiWithoutLicense = Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE;
  if (skipInCiWithoutLicense) {
    return;
  }

  let newTenantId: string | null = null;

  const ATOMIC_RED_TEAM_DISPLAY_NAME = `Atomic Red Team E2E ${Date.now()}`;

  test.beforeEach(async ({ request }) => {
    const tenantName = `Tenant Collector E2E ${Date.now()}`;
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

  test('should install Atomic Red Team collector with name only and show it deployed', async ({ page }) => {
    expect(newTenantId).not.toBeNull();

    // Step: deploy Atomic Red Team from catalog with only display name
    const catalogPage = new CatalogPage(page);
    await page.goto(tenantUrl('/admin/integrations/available', newTenantId!));
    await catalogPage.waitForLoad();

    await catalogPage.searchConnector('Atomic Red Team');
    await catalogPage.clickDeployOnConnector('Atomic Red Team');
    await catalogPage.fillDisplayName(ATOMIC_RED_TEAM_DISPLAY_NAME);
    await catalogPage.submitInstall();

    // Step: verify Atomic Red Team is installed on collectors list
    await page.goto(tenantUrl('/admin/integrations/deployed', newTenantId!));
    const deployedConnectorsPage = new InjectorsListPage(page);
    await deployedConnectorsPage.waitForLoad();

    const atomicCollectorCard = deployedConnectorsPage.getInjectorCard(ATOMIC_RED_TEAM_DISPLAY_NAME).first();
    await expect(
      atomicCollectorCard,
      `Expected deployed Atomic Red Team collector card "${ATOMIC_RED_TEAM_DISPLAY_NAME}" to be visible`,
    ).toBeVisible({ timeout: TIMEOUT });

    // Step: open collector and ensure it is in stopped state and can be started manually
    await atomicCollectorCard.click();
    await page.waitForURL('**/integrations/collectors/**');

    const startButton = page.getByRole('button', { name: 'Start' }).first();
    await expect(startButton).toBeVisible({ timeout: TIMEOUT });
    await expect(page.getByText('Stopped', { exact: true })).toBeVisible({ timeout: TIMEOUT });
  });
});
