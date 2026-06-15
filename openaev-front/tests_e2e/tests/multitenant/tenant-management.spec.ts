import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import TenantSwitcherComponent from '../../model/nav/TenantSwitcherComponent';
import TenantsPage from '../../model/platform/TenantsPage';
import { TIMEOUT } from '../../utils/constants';
import { DEFAULT_TENANT_UUID, tenantUrl } from '../../utils/url';

/**
 * End-to-end tests: multi-tenancy — tenant creation and the tenant switcher.
 *
 * Prerequisites (full-stack environment required):
 *  - Enterprise Edition license active
 *  - Logged in as admin (admin@openaev.io)
 *  - MULTI_TENANCY feature flag enabled
 */
test.describe('Multi-tenancy — tenant management', () => {
  let newTenantId: string | null = null;

  test.afterEach(async ({ request }) => {
    if (newTenantId) {
      await new TenantApiHelpers(request).softDeleteTenant(newTenantId);
      newTenantId = null;
    }
  });

  test('create new tenant A', async ({ page }) => {
    const tenantName = `Tenant A E2E ${Date.now()}`;

    // ─────────────────────────────────────────────────
    // Step 1 — Navigate to Tenants management
    // ─────────────────────────────────────────────────
    // Arrange
    const tenantsPage = new TenantsPage(page);
    await page.goto(tenantUrl('/admin/settings/security/tenants'));
    await tenantsPage.waitForLoad();
    await expect(
      tenantsPage.createFabButton,
      'Enterprise Edition / multi-tenancy must be enabled: expected the tenant "Add" button to be visible.',
    ).toBeVisible({ timeout: TIMEOUT });

    // ─────────────────────────────────────────────────
    // Step 2 — Create Tenant A
    // ─────────────────────────────────────────────────
    // Act: open the drawer, fill the name, submit
    await tenantsPage.openCreateDrawer();
    await tenantsPage.fillTenantName(tenantName);
    await tenantsPage.submitCreate();

    // Assert: browser navigates into the new tenant (URL no longer contains DEFAULT_TENANT_UUID)
    await page.waitForURL(
      url => !url.toString().includes(DEFAULT_TENANT_UUID),
      { timeout: TIMEOUT },
    );

    // Extract the new tenant UUID from the current URL
    const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const segments = new URL(page.url()).pathname.split('/').filter(Boolean);
    newTenantId = segments.find(s => uuidPattern.test(s) && s !== DEFAULT_TENANT_UUID) ?? null;
    expect(newTenantId).not.toBeNull();

    // ─────────────────────────────────────────────────
    // Verify — Tenant switcher contains the expected tenants
    // ─────────────────────────────────────────────────
    const tenantSwitcher = new TenantSwitcherComponent(page);

    // Open the switcher while in Tenant A
    await tenantSwitcher.openSwitcher(tenantName);

    // Assert: required entries are present — Default and Tenant A
    await expect(tenantSwitcher.popoverTenantItems.filter({ hasText: 'Default' })).toHaveCount(1);
    await expect(tenantSwitcher.popoverTenantItems.filter({ hasText: tenantName })).toHaveCount(1);

    // ─────────────────────────────────────────────────
    // Step 3 — Tenant switcher navigation
    // ─────────────────────────────────────────────────
    // Act: select Default from the open popover
    await tenantSwitcher.selectTenantByName('Default');

    // Assert: URL switches back to the default tenant
    await page.waitForURL(
      url => url.toString().includes(DEFAULT_TENANT_UUID),
      { timeout: TIMEOUT },
    );

    // Assert: open switcher from Default — both tenants are still listed
    await tenantSwitcher.openSwitcher('Default');
    await expect(tenantSwitcher.popoverTenantItems.filter({ hasText: 'Default' })).toHaveCount(1);
    await expect(tenantSwitcher.popoverTenantItems.filter({ hasText: tenantName })).toHaveCount(1);

    // Act: switch back to Tenant A
    await tenantSwitcher.selectTenantByName(tenantName);

    // Assert: URL switches back to Tenant A
    await page.waitForURL(
      url => url.toString().includes(newTenantId!),
      { timeout: TIMEOUT },
    );
  });
});
