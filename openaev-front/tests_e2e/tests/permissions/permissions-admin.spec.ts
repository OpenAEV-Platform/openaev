import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import LeftMenuComponent from '../../model/LeftMenuComponent';
import ThreatArsenalListPage from '../../model/threat-arsenals/ThreatArsenalListPage';
import { MARKING_DEFINITIONS_URL } from '../../utils/markingDefinitions.constants';
import { PERSONAS } from '../../utils/personas';
import { tenantApiPath, tenantUrl } from '../../utils/url';

/**
 * Control for the observer refusals in `permissions-observer.spec.ts` : the same pages load for the admin, so a
 * refusal comes from the observer's capabilities, not from a disabled preview feature
 * (with MARKING off, the marking definitions route answers no access to everyone).
 */
test.describe('Admin persona', () => {
  test.use({ persona: 'admin' });

  test('API calls run as the admin', async ({ request }) => {
    const response = await request.get(tenantApiPath('/api/me'));

    expect(response.ok()).toBeTruthy();
    expect((await response.json()).user_email).toBe(PERSONAS.admin.email);
  });

  test('the threat arsenal has create button', async ({ page }) => {
    const leftMenu = new LeftMenuComponent(page);
    const threatArsenalList = new ThreatArsenalListPage(page);

    await page.goto(tenantUrl('/admin'));
    await leftMenu.goToThreatArsenal();
    await threatArsenalList.waitForLoad();

    await expect(threatArsenalList.addButton).toBeVisible();
  });

  test('the marking definitions page loads', async ({ page }) => {
    await page.goto(MARKING_DEFINITIONS_URL);

    await expect(page.getByRole('button', {
      name: 'Add a marking definition',
      exact: true,
    })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  });
});
