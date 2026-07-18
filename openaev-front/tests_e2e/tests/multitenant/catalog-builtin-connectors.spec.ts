import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import InjectorsListPage from '../../model/integrations/InjectorsListPage';
import TenantSwitcherComponent from '../../model/nav/TenantSwitcherComponent';
import TenantsPage from '../../model/platform/TenantsPage';
import { TIMEOUT } from '../../utils/constants';
import { DEFAULT_TENANT_UUID, tenantUrl } from '../../utils/url';

const APP_URL = process.env.APP_URL ?? 'http://localhost:3001';

/**
 * End-to-end tests: built-in connectors provisioned on new tenant creation.
 */
test.describe('Multi-tenancy — built-in connectors', () => {
  const skipInCiWithoutLicense = Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE;
  if (skipInCiWithoutLicense) {
    return;
  }

  let newTenantId: string | null = null;

  // Dummy placeholder injectors are gone: starter-pack contracts are now
  // imported injector-less and adopted by the real injector on registration.
  const BUILTIN_INJECTORS = [
    'Challenges',
    'Email',
    'Manual',
    'Media pressure',
    'OpenAEV Implant',
  ];

  const BUILTIN_COLLECTORS = [
    'Expectations Expiration Manager',
    'Expectations Vulnerability Manager',
  ];

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'tests_e2e/.auth/user.json',
      baseURL: APP_URL,
    });
    const page = await context.newPage();

    // Create Tenant A
    const tenantsPage = new TenantsPage(page);
    await page.goto(tenantUrl('/admin/settings/security/tenants'));
    await tenantsPage.waitForLoad();
    await expect(
      tenantsPage.createFabButton,
      'Enterprise Edition / multi-tenancy must be enabled: expected the tenant "Add" button to be visible.',
    ).toBeVisible({ timeout: TIMEOUT });
    await tenantsPage.openCreateDrawer();
    const tenantName = `Tenant Builtin E2E ${Date.now()}`;
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

    await context.close();
  });

  test.afterAll(async ({ browser }) => {
    if (newTenantId) {
      const context = await browser.newContext({
        storageState: 'tests_e2e/.auth/user.json',
        baseURL: APP_URL,
      });
      const request = context.request;
      await new TenantApiHelpers(request).softDeleteTenant(newTenantId);
      await context.close();
      newTenantId = null;
    }
  });

  test('should have built-in connectors installed when creating a new tenant', async ({ page }) => {
    expect(newTenantId).not.toBeNull();

    // ─── Verify built-in injectors ───
    const injectorsListPage = new InjectorsListPage(page);
    await page.goto(tenantUrl('/admin/integrations/deployed', newTenantId!));
    await injectorsListPage.waitForLoad();

    await Promise.all(
      BUILTIN_INJECTORS.map(async (injectorName) => {
        await expect(
          page.locator('.MuiCard-root').filter({ hasText: injectorName }).first(),
          `Expected built-in injector "${injectorName}" to be visible`,
        ).toBeVisible({ timeout: TIMEOUT });
      }),
    );

    // ─── Verify built-in collectors ───
    await page.goto(tenantUrl('/admin/integrations/deployed', newTenantId!));
    await page.waitForURL('**/integrations/deployed**');

    await Promise.all(
      BUILTIN_COLLECTORS.map(async (collectorName) => {
        await expect(
          page.locator('.MuiCard-root').filter({ hasText: collectorName }).first(),
          `Expected built-in collector "${collectorName}" to be visible`,
        ).toBeVisible({ timeout: TIMEOUT });
      }),
    );

    // ─── Verify executors page is accessible ───
    await page.goto(tenantUrl('/admin/integrations/deployed', newTenantId!));
    await page.waitForURL('**/integrations/deployed**');
    // At minimum, the OpenAEV Agent executor should be listed
    await expect(
      page.locator('.MuiCard-root').filter({ hasText: /OpenAEV/i }).first(),
      'Expected at least one executor (OpenAEV) to be visible',
    ).toBeVisible({ timeout: TIMEOUT });
  });
});
