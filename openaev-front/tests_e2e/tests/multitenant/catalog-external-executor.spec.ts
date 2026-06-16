import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import CatalogPage from '../../model/integrations/CatalogPage';
import TenantsPage from '../../model/platform/TenantsPage';
import { TIMEOUT } from '../../utils/constants';
import { DEFAULT_TENANT_UUID, tenantUrl } from '../../utils/url';

/**
 * End-to-end test: install an external executor (Tanium) in a new tenant.
 *
 * Prerequisites (full-stack environment required):
 *  - Enterprise Edition license active
 *  - MULTI_TENANCY feature flag enabled
 *  - XTM Composer running
 */
test.describe('Catalog — external executor deployment', () => {
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

  test('should install Tanium executor with name only and show it deployed', async ({ page }) => {
    expect(newTenantId).not.toBeNull();

    // Step: deploy Tanium from catalog with only display name
    const catalogPage = new CatalogPage(page);
    await page.goto(tenantUrl('/admin/integrations/catalog', newTenantId!));
    await catalogPage.waitForLoad();

    await catalogPage.searchConnector('Tanium');
    await catalogPage.clickDeployOnConnector('Tanium');
    await catalogPage.fillDisplayName(TANIUM_DISPLAY_NAME);
    await catalogPage.submitInstall();

    // Step: verify Tanium is installed on executors list
    await page.goto(tenantUrl('/admin/integrations/executors', newTenantId!));
    await page.waitForURL('**/integrations/executors**');

    const taniumCard = page.locator('.MuiCard-root').filter({ hasText: TANIUM_DISPLAY_NAME }).first();
    await expect(
      taniumCard,
      `Expected deployed Tanium executor card "${TANIUM_DISPLAY_NAME}" to be visible`,
    ).toBeVisible({ timeout: TIMEOUT });

    // Step: open executor and ensure it is deployed/running
    await taniumCard.click();
    await page.waitForURL('**/integrations/executors/**');

    const startButton = page.getByRole('button', { name: 'Start' });
    if ((await startButton.count()) > 0 && (await startButton.first().isVisible())) {
      await startButton.first().click();
    }

    await expect(page.getByText('Started', { exact: true })).toBeVisible({ timeout: 120_000 });
  });
});

