import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import TenantSwitcherComponent from '../../model/nav/TenantSwitcherComponent';
import TenantsPage from '../../model/platform/TenantsPage';
import { TIMEOUT } from '../../utils/constants';
import { DEFAULT_TENANT_UUID, tenantUrl } from '../../utils/url';

/**
 * End-to-end tests: multi-tenancy — tenant creation and the tenant switcher.
 */
test.describe('Multi-tenancy — tenant management', () => {
  test.skip(
    Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE,
    'Requires OPENAEV_APPLICATION_LICENSE in CI (fork PRs may not expose license)',
  );
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

    // ─────────────────────────────────────────────────
    // Step 3 — Switch to the new tenant from the tenant switcher
    // ─────────────────────────────────────────────────
    const tenantSwitcher = new TenantSwitcherComponent(page);
    await tenantSwitcher.openSwitcher('Default');
    await expect(tenantSwitcher.popoverTenantItems.filter({ hasText: tenantName })).toHaveCount(1);
    await tenantSwitcher.selectTenantByName(tenantName);
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

    // Open the switcher while in Tenant A
    await tenantSwitcher.openSwitcher(tenantName);

    // Assert: required entries are present — Default and Tenant A
    await expect(tenantSwitcher.popoverTenantItems.filter({ hasText: 'Default' })).toHaveCount(1);
    await expect(tenantSwitcher.popoverTenantItems.filter({ hasText: tenantName })).toHaveCount(1);

    // ─────────────────────────────────────────────────
    // Step 4 — Tenant switcher navigation
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
