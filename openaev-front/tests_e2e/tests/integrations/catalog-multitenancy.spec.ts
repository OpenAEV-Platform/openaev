import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import CatalogPage from '../../model/integrations/CatalogPage';
import InjectorsListPage from '../../model/integrations/InjectorsListPage';
import InjectorInstancePage from '../../model/integrations/InjectorInstancePage';
import LeftMenuComponent from '../../model/LeftMenuComponent';
import TenantSwitcherComponent from '../../model/nav/TenantSwitcherComponent';
import TenantsPage from '../../model/platform/TenantsPage';
import ThreatArsenalListPage from '../../model/threat-arsenals/ThreatArsenalListPage';
import { DEFAULT_TENANT_UUID, tenantUrl } from '../../utils/url';
import { TIMEOUT } from '../../utils/constants';

/**
 * End-to-end test: catalog multi-tenancy isolation.
 *
 * Prerequisites (full-stack environment required):
 *  - Enterprise Edition license active
 *  - Logged in as sadmin (admin@openaev.io)
 *  - Nmap catalog connector present in the catalog
 *  - For steps 3-4: a running Nmap collector agent connected via XTM Composer
 *
 * Flow:
 *  1.  (admin) Navigate to Platform > Tenants management
 *  2.  Create new Tenant A with a unique name
 *  3.  In Tenant A: go to Integrations > Catalog, deploy Nmap named "Nmap - Tenant A"
 *  4.  In Tenant A: open the deployed collector, click Start, wait for "Started" status
 *  5.  In Tenant A: go to Threat Arsenal, search "Nmap", verify contracts are present
 *  6.  Switch tenant to the default tenant via the left-bar tenant switcher
 *  7.  In default tenant: Integrations > Injectors — verify "Nmap - Tenant A" is absent
 */
test.describe('Catalog — multi-tenancy isolation', () => {
  const NMAP_INJECTOR_NAME = 'Nmap - Tenant A';
  // UUID of the newly created tenant — populated during the test, used for cleanup
  let newTenantId: string | null = null;

  test.afterEach(async ({ request }) => {
    // Best-effort teardown: soft-delete the tenant created during the test
    if (newTenantId) {
      try {
        await new TenantApiHelpers(request).softDeleteTenant(newTenantId);
      } catch {
        // Ignore cleanup errors (e.g. EE disabled, tenant already deleted)
      }
      newTenantId = null;
    }
  });

  test('should be able to install an injector in a tenant', async ({ page }) => {
    const tenantName = `Tenant A E2E ${Date.now()}`;
    // ─────────────────────────────────────────────────
    // Step 1 & 2 — Create Tenant A
    // ─────────────────────────────────────────────────
    // Arrange
    const tenantsPage = new TenantsPage(page);
    await page.goto(tenantUrl('/admin/settings/security/tenants'));
    await tenantsPage.waitForLoad();
    // Act: open the drawer, fill the name, submit
    await tenantsPage.openCreateDrawer();
    await tenantsPage.fillTenantName(tenantName);
    await tenantsPage.submitCreate();
    // Assert: the browser navigates to the new tenant (URL no longer contains DEFAULT_TENANT_UUID)
    await page.waitForURL(
      url => !url.toString().includes(DEFAULT_TENANT_UUID),
      { timeout: TIMEOUT },
    );
    // Extract the new tenant UUID from the current URL pathname (first UUID segment)
    const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const segments = new URL(page.url()).pathname.split('/').filter(Boolean);
    newTenantId = segments.find(s => uuidPattern.test(s) && s !== DEFAULT_TENANT_UUID) ?? null;
    expect(newTenantId).not.toBeNull();
    // ─────────────────────────────────────────────────
    // Step 3 — Install Nmap from the Catalog in Tenant A
    // ─────────────────────────────────────────────────
    // Arrange
    const catalogPage = new CatalogPage(page);
    await page.goto(tenantUrl('/admin/integrations/catalog', newTenantId!));
    await catalogPage.waitForLoad();
    // Act: search for Nmap and click Deploy
    await catalogPage.searchConnector('Nmap');
    await catalogPage.clickDeployOnConnector('Nmap');
    // Fill the instance display name and submit
    await catalogPage.fillDisplayName(NMAP_INJECTOR_NAME);
    await catalogPage.submitInstall();
    // ─────────────────────────────────────────────────
    // Step 3 (cont.) — Navigate to the deployed injector and start it
    // ─────────────────────────────────────────────────
    // Navigate to the injectors list (catalog may or may not auto-redirect)
    const injectorsListPage = new InjectorsListPage(page);
    await page.goto(tenantUrl('/admin/integrations/injectors', newTenantId!));
    await injectorsListPage.waitForLoad();
    // Wait for the connector instance to appear as a (possibly pending) card
    await injectorsListPage.waitForConnectorToAppear(NMAP_INJECTOR_NAME);
    // Click the card to open the injector detail page
    await injectorsListPage.clickOnInjector(NMAP_INJECTOR_NAME);
    // ─────────────────────────────────────────────────
    // Step 4 — Start the injector; wait for "Started"
    // ─────────────────────────────────────────────────
    // Arrange
    const injectorInstancePage = new InjectorInstancePage(page);
    await injectorInstancePage.waitForLoad();
    // Act: click "Start" (sets requestedStatus = starting)
    await injectorInstancePage.clickStart();
    // Assert: wait for currentStatus to reach "started"
    // This is async and driven by the actual Nmap container start-up sequence.
    await injectorInstancePage.waitForStarted();
    await expect(injectorInstancePage.startedChip).toBeVisible();
    // ─────────────────────────────────────────────────
    // Step 5 — Verify Threat Arsenal shows Nmap contracts
    // ─────────────────────────────────────────────────
    // Arrange: navigate to Threat Arsenal via the left menu
    const leftMenu = new LeftMenuComponent(page);
    await leftMenu.goToThreatArsenal();
    const threatArsenalList = new ThreatArsenalListPage(page);
    await threatArsenalList.waitForLoad();
    // Act: search for Nmap-related contracts
    await threatArsenalList.searchThreatArsenal('Nmap');
    // Assert: at least one result row is visible
    await expect(threatArsenalList.getItem(1)).toBeVisible();
    // ─────────────────────────────────────────────────
    // Step 6 — Switch tenant to the default tenant
    // ─────────────────────────────────────────────────
    // Act: open the left-bar tenant switcher and pick any tenant that is NOT Tenant A
    const tenantSwitcher = new TenantSwitcherComponent(page);
    await tenantSwitcher.openSwitcher(tenantName);
    await tenantSwitcher.selectFirstOtherTenant(tenantName);
    // Assert: URL now contains the default tenant UUID
    await page.waitForURL(
      url => url.toString().includes(DEFAULT_TENANT_UUID),
      { timeout: TIMEOUT },
    );
    // ─────────────────────────────────────────────────
    // Step 7 — Verify "Nmap - Tenant A" is NOT visible in the default tenant
    // ─────────────────────────────────────────────────
    // Arrange: navigate to the injectors list in the default tenant
    await page.goto(tenantUrl('/admin/integrations/injectors'));
    await injectorsListPage.waitForLoad();
    await page.waitForLoadState('networkidle');
    // Assert: the collector name from Tenant A must not appear here
    await expect(page.getByText(NMAP_INJECTOR_NAME)).toBeHidden();
  });
});
