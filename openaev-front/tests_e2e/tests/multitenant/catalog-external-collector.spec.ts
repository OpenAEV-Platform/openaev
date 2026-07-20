import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import CatalogPage from '../../model/integrations/CatalogPage';
import TenantSwitcherComponent from '../../model/nav/TenantSwitcherComponent';
import TenantsPage from '../../model/platform/TenantsPage';
import { TIMEOUT } from '../../utils/constants';
import { DEFAULT_TENANT_UUID, tenantUrl } from '../../utils/url';

/**
 * End-to-end test: install an external collector (Atomic Red Team) in a new tenant.
 */
test.describe('Catalog — external collector deployment', () => {
  const skipInCiWithoutLicense = Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE;
  if (skipInCiWithoutLicense) {
    return;
  }

  let newTenantId: string | null = null;
  let tenantName: string;

  const ATOMIC_RED_TEAM_DISPLAY_NAME = `Atomic Red Team E2E ${Date.now()}`;

  test.beforeEach(async ({ page }) => {
    tenantName = `Tenant Collector E2E ${Date.now()}`;
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
    await page.waitForURL('**/integrations/deployed**');

    const atomicCollectorCard = page.locator('.MuiCard-root').filter({ hasText: ATOMIC_RED_TEAM_DISPLAY_NAME }).first();
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
