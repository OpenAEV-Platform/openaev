import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import CatalogPage from '../../model/integrations/CatalogPage';
import InjectorInstancePage from '../../model/integrations/InjectorInstancePage';
import InjectorsListPage from '../../model/integrations/InjectorsListPage';
import LeftMenuComponent from '../../model/LeftMenuComponent';
import ThreatArsenalListPage from '../../model/threat-arsenals/ThreatArsenalListPage';
import { tenantUrl } from '../../utils/url';

/**
 * End-to-end test: catalog injector installation per tenant.
 */
test.describe('Catalog — injector installation per tenant', () => {
  const skipInCiWithoutLicense = Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE;
  if (skipInCiWithoutLicense) {
    return;
  }

  const NMAP_INJECTOR_NAME = 'Nmap - Tenant A';
  let newTenantId: string | null = null;

  test.beforeEach(async ({ request }) => {
    const tenantName = `Tenant A E2E ${Date.now()}`;
    const createdTenant = await new TenantApiHelpers(request).createTenant(tenantName);
    newTenantId = createdTenant.tenant_id;
  });

  test.afterEach(async ({ request }) => {
    if (newTenantId) {
      await new TenantApiHelpers(request).softDeleteTenant(newTenantId);
      newTenantId = null;
    }
  });

  test('should be able to install an external injector in a new tenant', async ({ page }) => {
    expect(newTenantId).not.toBeNull();
    // ─────────────────────────────────────────────────
    // Step - Install Nmap Injector from the Catalog in Tenant A
    // ─────────────────────────────────────────────────
    // Arrange
    const catalogPage = new CatalogPage(page);
    await page.goto(tenantUrl('/admin/integrations/available', newTenantId!));
    await catalogPage.waitForLoad();
    // Act: search for Nmap and click Deploy
    await catalogPage.searchConnector('Nmap');
    await catalogPage.clickDeployOnConnector('Nmap');
    // Fill the instance display name and submit
    await catalogPage.fillDisplayName(NMAP_INJECTOR_NAME);
    await catalogPage.submitInstall();

    // ─────────────────────────────────────────────────
    // Step — Navigate to the deployed injector and start it
    // ─────────────────────────────────────────────────
    // Navigate to the injectors list (catalog may or may not auto-redirect)
    const injectorsListPage = new InjectorsListPage(page);
    await page.goto(tenantUrl('/admin/integrations/deployed', newTenantId!));
    await injectorsListPage.waitForLoad();
    // Wait for the connector instance to appear as a (possibly pending) card
    await injectorsListPage.waitForConnectorToAppear(NMAP_INJECTOR_NAME);
    // Click the card to open the injector detail page
    await injectorsListPage.clickOnInjector(NMAP_INJECTOR_NAME);

    // ─────────────────────────────────────────────────
    // Step — Start the injector; wait for "Started"
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
    // Step — Verify Threat Arsenal shows injector's contracts
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
    // Step — Verify "Nmap - Tenant A" is NOT visible in the default tenant
    // ─────────────────────────────────────────────────
    // Arrange: navigate to the injectors list in the default tenant
    await page.goto(tenantUrl('/admin/integrations/deployed'));
    await injectorsListPage.waitForLoad();
    // Assert: the collector name from Tenant A must not appear here
    await expect(injectorsListPage.getInjectorCard(NMAP_INJECTOR_NAME)).toHaveCount(0);
  });
});
