import { expect } from '@playwright/test';

import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import InjectorsListPage from '../../model/integrations/InjectorsListPage';
import { TIMEOUT } from '../../utils/constants';
import { tenantUrl } from '../../utils/url';

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

  test.beforeAll(async ({ request }) => {
    const tenantName = `Tenant Builtin E2E ${Date.now()}`;
    const createdTenant = await new TenantApiHelpers(request).createTenant(tenantName);
    newTenantId = createdTenant.tenant_id;
    expect(newTenantId).not.toBeNull();
  });

  test.afterAll(async ({ request }) => {
    if (newTenantId) {
      await new TenantApiHelpers(request).softDeleteTenant(newTenantId);
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
          injectorsListPage.getInjectorCard(injectorName).first(),
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
          injectorsListPage.getInjectorCard(collectorName).first(),
          `Expected built-in collector "${collectorName}" to be visible`,
        ).toBeVisible({ timeout: TIMEOUT });
      }),
    );

    // ─── Verify executors page is accessible ───
    await page.goto(tenantUrl('/admin/integrations/deployed', newTenantId!));
    await page.waitForURL('**/integrations/deployed**');
    // At minimum, the OpenAEV Agent executor should be listed
    await expect(
      injectorsListPage.getInjectorCard(/OpenAEV/i).first(),
      'Expected at least one executor (OpenAEV) to be visible',
    ).toBeVisible({ timeout: TIMEOUT });
  });
});
